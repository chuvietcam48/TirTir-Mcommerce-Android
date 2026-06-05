const http = require('http');
const mongoose = require('mongoose');

require('dotenv').config();

const User = require('../models/user.model');
const Order = require('../models/order.model');
const Coupon = require('../models/coupon.model');

const API_HOST = 'localhost';
const API_PORT = Number(process.env.PORT || 5001);

function httpJson({ method, path, body, headers = {} }) {
  return new Promise((resolve, reject) => {
    const payload = body ? JSON.stringify(body) : '';
    const req = http.request(
      {
        host: API_HOST,
        port: API_PORT,
        path,
        method,
        headers: {
          'Content-Type': 'application/json',
          ...(payload ? { 'Content-Length': Buffer.byteLength(payload) } : {}),
          ...headers,
        },
      },
      (res) => {
        let raw = '';
        res.setEncoding('utf8');
        res.on('data', (chunk) => (raw += chunk));
        res.on('end', () => {
          let parsed = {};
          try {
            parsed = raw ? JSON.parse(raw) : {};
          } catch {
            parsed = { raw };
          }
          resolve({ status: res.statusCode, body: parsed });
        });
      }
    );

    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

async function ensureAuthUser() {
  const stamp = Date.now();
  const email = `chat.e2e.${stamp}@example.com`;
  const password = 'P@ssw0rd123';

  const registerResp = await httpJson({
    method: 'POST',
    path: '/api/v1/auth/register',
    body: {
      name: 'Chat E2E User',
      firstName: 'Chat',
      lastName: 'E2E',
      email,
      password,
    },
  });

  if (registerResp.status >= 400 || !registerResp.body?.token) {
    throw new Error(`Register failed: ${registerResp.status} ${JSON.stringify(registerResp.body)}`);
  }

  const user = await User.findOne({ email }).select('_id email').lean();
  if (!user) throw new Error('Registered user not found');

  return { token: registerResp.body.token, user, email };
}

async function seedData(userId) {
  const now = new Date();
  const validTo = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);

  await Coupon.findOneAndUpdate(
    { code: 'AUTH10' },
    {
      $set: {
        code: 'AUTH10',
        discountType: 'percentage',
        discountValue: 10,
        minOrderValue: 200000,
        maxDiscount: 100000,
        validFrom: now,
        validTo,
        usageLimit: 200,
        usedCount: 0,
        active: true,
      },
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );

  await Order.findOneAndUpdate(
    { user: userId, trackingNumber: 'ORD-AUTH-001' },
    {
      $set: {
        user: userId,
        items: [
          {
            product: new mongoose.Types.ObjectId(),
            name: 'Test Cushion',
            quantity: 1,
            price: 450000,
          },
        ],
        shippingAddress: {
          fullName: 'Chat E2E User',
          phone: '0900000000',
          address: '123 Test Street',
          ward: 'Ward 1',
          district: 'District 1',
          city: 'HCMC',
        },
        paymentMethod: 'MOMO',
        status: 'Shipped',
        trackingNumber: 'ORD-AUTH-001',
        ghnOrderCode: 'GHN-AUTH-001',
        orderStatus: 'SHIPPING',
        totalAmount: 450000,
        expectedDeliveryDate: new Date(now.getTime() + 2 * 24 * 60 * 60 * 1000),
      },
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );
}

function parseEventBlock(block) {
  const lines = block.split('\n').map((line) => line.trim()).filter(Boolean);
  if (!lines.length) return null;
  const eventLine = lines.find((line) => line.startsWith('event:'));
  const eventName = eventLine ? eventLine.slice(6).trim() : 'message';
  const dataRaw = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
    .join('\n');

  let payload = {};
  try {
    payload = dataRaw ? JSON.parse(dataRaw) : {};
  } catch {
    payload = { raw: dataRaw };
  }

  return { eventName, payload };
}

function runSseCase(name, message, token) {
  return new Promise((resolve) => {
    const body = JSON.stringify({ message });
    const req = http.request(
      {
        host: API_HOST,
        port: API_PORT,
        path: '/api/v1/chat',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body),
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
      },
      (res) => {
        console.log(`===== ${name} =====`);
        console.log(`HTTP ${res.statusCode} | Content-Type: ${res.headers['content-type'] || ''}`);

        let buffer = '';
        res.setEncoding('utf8');
        res.on('data', (chunk) => {
          buffer += chunk;
          let split = buffer.indexOf('\n\n');
          while (split !== -1) {
            const block = buffer.slice(0, split);
            buffer = buffer.slice(split + 2);
            const parsed = parseEventBlock(block);
            if (parsed) {
              const { eventName, payload } = parsed;
              console.log(`[EVENT] ${eventName}`);
              if (eventName === 'chunk') {
                console.log(`  chunk: ${(payload.text || '').slice(0, 140)}`);
              } else if (eventName === 'done') {
                const data = payload.data || {};
                console.log(`  intent: ${data.intent || 'N/A'}`);
                console.log(`  type: ${data.type || 'N/A'}`);
                console.log(`  message: ${(data.message || '').slice(0, 320)}`);
              } else if (eventName === 'error') {
                console.log(`  error: ${JSON.stringify(payload)}`);
              }
            }
            split = buffer.indexOf('\n\n');
          }
        });
        res.on('end', () => {
          if (buffer.trim()) {
            const parsed = parseEventBlock(buffer);
            if (parsed) {
              console.log(`[EVENT] ${parsed.eventName}`);
              console.log(`  payload: ${JSON.stringify(parsed.payload).slice(0, 320)}`);
            }
          }
          console.log('----- END CASE -----');
          resolve();
        });
      }
    );

    req.on('error', (err) => {
      console.log(`===== ${name} =====`);
      console.log(`[FAILED] ${err.message}`);
      console.log('----- END CASE -----');
      resolve();
    });

    req.write(body);
    req.end();
  });
}

(async () => {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    const { token, user, email } = await ensureAuthUser();
    await seedData(user._id);

    console.log('===== AUTH E2E SETUP =====');
    console.log(`User: ${email} | userId: ${user._id}`);
    console.log('Seeded coupon AUTH10 and order ORD-AUTH-001');

    await runSseCase('AUTH E2E CASE 1: COUPON', 'Cho mình mã giảm giá đang active', token);
    await runSseCase('AUTH E2E CASE 2: ORDER STATUS', 'Kiểm tra đơn hàng mã ORD-AUTH-001 giúp mình', token);
    await runSseCase('AUTH E2E CASE 3: CONTEXT FOLLOW-UP', 'Đơn đó đang shipping đúng không?', token);

    console.log('===== AUTH BACKEND E2E TEST COMPLETE =====');
  } catch (err) {
    console.error('[AUTH E2E TEST FAILED]', err.message);
    process.exitCode = 1;
  } finally {
    await mongoose.disconnect().catch(() => {});
  }
})();
