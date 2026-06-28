const cron = require('node-cron');
const admin = require('firebase-admin');
const Product = require('../models/Product');
const fcmService = require('../services/fcmService');

/**
 * Execute cart recovery scan
 */
async function runCartRecoveryJob() {
  console.log('[CART_RECOVERY] Running automated cart recovery job...');
  try {
    const db = admin.firestore();
    const snapshot = await db.collection('carts')
      .where('status', '==', 'active')
      .get();

    const now = new Date();
    const twentyFourHoursAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);

    for (const doc of snapshot.docs) {
      const data = doc.data();
      const userId = doc.id || data.userId;

      if (!data.items || data.items.length === 0) continue;

      const recoveryNotified = data.recoveryNotified || 0;
      if (recoveryNotified >= 2) continue; // Max 2 pushes per cart

      let lastUpdatedAt = null;
      if (data.lastUpdatedAt) {
        lastUpdatedAt = data.lastUpdatedAt.toDate ? data.lastUpdatedAt.toDate() : new Date(data.lastUpdatedAt);
      }

      if (!lastUpdatedAt || lastUpdatedAt > twentyFourHoursAgo) continue;

      // Get first product name
      const firstItem = data.items[0];
      let productName = firstItem.name || 'sản phẩm';
      if (!firstItem.name && firstItem.productId) {
        try {
          const prod = await Product.findOne({
            $or: [{ Product_ID: firstItem.productId }, { _id: firstItem.productId }]
          });
          if (prod) productName = prod.Name;
        } catch (e) {}
      }

      const payload = {
        notification: {
          title: 'Bạn còn sản phẩm trong giỏ!',
          body: `Bạn còn ${productName} đang chờ thanh toán.`
        },
        data: {
          screen: 'CART',
          type: 'CART_RECOVERY'
        }
      };

      try {
        const pushResult = await fcmService.sendToUser(userId, payload, { type: 'CART_RECOVERY' });
        if (pushResult && (pushResult.successCount > 0 || pushResult.success !== false)) {
          await db.collection('carts').doc(doc.id).update({
            recoveryNotified: recoveryNotified + 1,
            lastRecoveryNotifiedAt: admin.firestore.FieldValue.serverTimestamp()
          });
          console.log(`[CART_RECOVERY] Successfully sent recovery push to user ${userId}.`);
        }
      } catch (sendErr) {
        console.error(`[CART_RECOVERY] Failed to send push notification to user ${userId}:`, sendErr.message);
      }
    }
  } catch (err) {
    console.error('[CART_RECOVERY] Cron job error:', err.message);
  }
}

// Schedule cron job every hour ('0 * * * *')
const cartRecoverySchedule = cron.schedule('0 * * * *', runCartRecoveryJob, {
  scheduled: false
});

module.exports = {
  start: () => {
    if (process.env.CART_RECOVERY_CRON_ENABLED !== 'false') {
      cartRecoverySchedule.start();
      console.log('[CART_RECOVERY] Cron job initialized (Every hour: 0 * * * *)');
    }
  },
  runCartRecoveryJob
};

