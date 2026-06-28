const cron = require('node-cron');
const admin = require('firebase-admin');
const Product = require('../models/Product');
const User = require('../models/User');

/**
 * Helper to retrieve FCM tokens for a user ID
 */
async function getUserDeviceTokens(userId) {
  let tokens = [];
  try {
    const user = await User.findById(userId);
    if (user && user.fcmTokens) {
      tokens = user.fcmTokens.filter(t => t.active !== false).map(t => t.token || t);
    }
  } catch (err) {
    console.error('[CART_RECOVERY] Error reading user FCM tokens from MongoDB:', err.message);
  }

  if (tokens.length > 0) return tokens;

  try {
    const db = admin.firestore();
    const userDoc = await db.collection('users').doc(String(userId)).get();
    if (userDoc.exists && userDoc.data().fcmTokens) {
      tokens = userDoc.data().fcmTokens.filter(t => t.active !== false).map(t => t.token || t);
    }
  } catch (err) {
    console.error('[CART_RECOVERY] Error reading user FCM tokens from Firestore:', err.message);
  }

  return tokens;
}

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
      if (recoveryNotified >= 2) continue;

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
        } catch (e) {
          // ignore fallback
        }
      }

      const tokens = await getUserDeviceTokens(userId);
      if (tokens.length === 0) {
        console.log(`[CART_RECOVERY] No device token found for user ${userId}`);
        continue;
      }

      const messagePayload = {
        notification: {
          title: 'Giỏ hàng của bạn',
          body: `You still have ${productName} waiting in your cart.`
        },
        data: {
          screen: 'cart',
          type: 'cart_recovery'
        }
      };

      try {
        const response = await admin.messaging().sendMulticast({
          tokens,
          ...messagePayload
        });

        console.log(`[CART_RECOVERY] Notification sent to user ${userId}. Success count: ${response.successCount}`);

        await db.collection('carts').doc(doc.id).update({
          recoveryNotified: recoveryNotified + 1,
          lastRecoveryNotifiedAt: admin.firestore.FieldValue.serverTimestamp()
        });
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
    cartRecoverySchedule.start();
    console.log('[CART_RECOVERY] Cron job initialized (Every hour: 0 * * * *)');
  },
  runCartRecoveryJob
};
