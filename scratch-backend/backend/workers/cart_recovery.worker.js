const { Worker } = require('bullmq');
const Redis = require('ioredis');
const mongoose = require('mongoose');
const { connection } = require('../queues/cart_recovery.queue');
const CartRecoveryEvent = require('../models/cart_recovery_event.model');
const EmailSendLog = require('../models/email_send_log.model');
const Order = require('../models/order.model');
const Cart = require('../models/cart.model');

const redisClient = new Redis(connection.host ? connection : process.env.REDIS_URL || 'redis://127.0.0.1:6379');

async function acquireIdempotencyLock(key, ttlSeconds = 60) {
  const result = await redisClient.set(`lock:${key}`, 'locked', 'EX', ttlSeconds, 'NX');
  return result === 'OK';
}

const nodemailer = require('nodemailer');
const { escapeHtml, formatVND } = require('../utils/email.helper');

const transporter = nodemailer.createTransport({
  host: process.env.MAILTRAP_HOST,
  port: process.env.MAILTRAP_PORT,
  auth: {
    user: process.env.MAILTRAP_USER,
    pass: process.env.MAILTRAP_PASS,
  },
});

async function sendTemplateToESP({ to, templateData, emailSequence }) {
  const subjects = {
    1: 'Bạn còn quên món đồ trong giỏ hàng!',
    2: 'Ưu đãi đặc biệt dành riêng cho bạn',
    3: 'Cơ hội cuối — giỏ hàng sắp hết hiệu lực',
  };

  const itemsHtml = (templateData.items || []).map(item => `
    <div style="display:flex; margin-bottom: 12px; border-bottom: 1px solid #eee; padding-bottom: 8px;">
      <div>
        <strong style="font-size:14px; color:#333;">${escapeHtml(item.product?.Name || 'Sản phẩm')}</strong><br/>
        <small style="color:#666;">Màu: ${escapeHtml(item.shade || 'Mặc định')} x ${item.quantity}</small><br/>
        <span style="color:#d32f2f; font-weight:bold;">${formatVND(item.product?.Price || 0)}</span>
      </div>
    </div>
  `).join('');

  const html = `
    <h2>Xin chào!</h2>
    <p>Bạn đang có sản phẩm chưa thanh toán trong giỏ hàng.</p>
    <div style="background:#fafafa; padding:16px; margin: 16px 0; border: 1px solid #eaeaea; border-radius: 8px;">
      ${itemsHtml}
    </div>
    <a href="${process.env.FRONTEND_URL}/checkout?recovery_token=${templateData.recovery_token}"
       style="background:#000;color:#fff;padding:12px 24px;text-decoration:none;border-radius:4px; display:inline-block;">
      Hoàn tất đơn hàng
    </a>
    <p style="font-size:12px;color:#999;margin-top:24px;">
      <a href="${process.env.FRONTEND_URL}/unsubscribe?token=${templateData.recovery_token}">
        Hủy nhận thông báo
      </a>
    </p>
  `;

  const info = await transporter.sendMail({
    from: process.env.MAIL_FROM,
    to,
    subject: subjects[emailSequence] || subjects[1],
    html,
  });

  console.log(`[ESP LOG] Sent Email ${emailSequence} to ${to} — messageId: ${info.messageId}`);
  return { messageId: info.messageId };
}

const worker = new Worker('cart-recovery-email', async (job) => {
  const { cartId, emailSequence, cartLastUpdatedAt, email } = job.data;
  const today = new Date().toISOString().split('T')[0].replace(/-/g, '');
  const idempotencyKey = `cart-recovery:${cartId}:${emailSequence}:${today}`;

  try {
    const locked = await acquireIdempotencyLock(idempotencyKey);
    if (!locked) {
      console.warn(`[WORKER] Race condition mitigated for ${idempotencyKey}.`);
      return 'skipped';
    }

    const existingLog = await EmailSendLog.findOne({ idempotency_key: idempotencyKey }).lean();
    if (existingLog && existingLog.status === 'sent') return 'skipped';

    const orderExists = await Order.findOne({
      $or: [{ email }, { cart_id: cartId }],
      created_at: { $gte: new Date(cartLastUpdatedAt) }
    }).lean();

    if (orderExists) {
      await Cart.updateOne({ _id: cartId }, { $set: { status: 'purchased' } });
      return 'aborted';
    }

    const cart = await Cart.findById(cartId).populate('items.product');
    if (!cart || cart.items.length === 0) return 'aborted';

    let espMessageId;
    try {
      const espResponse = await sendTemplateToESP({ to: email, templateData: cart, emailSequence });
      espMessageId = espResponse.messageId;
    } catch (espError) {
      throw new Error(`ESP_FAILED: ${espError.message}`);
    }

    try {
      await EmailSendLog.create({
        idempotency_key: idempotencyKey,
        cart_id: cartId,
        email_sequence: emailSequence,
        recipient_email: email,
        esp_message_id: espMessageId,
        status: 'sent',
        sent_at: new Date()
      });
    } catch (dbError) {
      if (EmailSendLog.isDuplicateKeyError(dbError)) {
        console.warn('E11000 - Key inserted by ghost worker, but ESP was triggered.');
      } else {
        throw dbError;
      }
    }

    await CartRecoveryEvent.create({
      cart_id: cartId,
      event_type: 'email_sent',
      metadata: { job_id: job.id, message_id: espMessageId }
    });

    console.log(`[WORKER] Completed recovery email ${emailSequence} to ${email}`);
    return 'sent';
  } catch (error) {
    await redisClient.del(`lock:${idempotencyKey}`);
    throw error;
  }
}, { connection });

worker.on('failed', async (job, err) => {
  if (job && job.attemptsMade === job.opts.attempts) {
    await CartRecoveryEvent.create({
      cart_id: job.data.cartId,
      event_type: 'job_failed',
      metadata: { job_id: job.id, error_reason: err.message }
    });
    console.error(`🚨 [URGENT] Cart Recovery Job in DLQ. Job ID: ${job.id}`);
  }
});
