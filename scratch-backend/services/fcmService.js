const admin = require('firebase-admin');
const firebaseAdmin = require('../backend/services/firebaseAdmin.service');
const User = require('../models/User');

/**
 * Log notification outcome to Firestore users/{userId}/notification_logs
 */
async function logNotification(userId, payload, status, extraData = {}) {
  if (!userId) return;
  try {
    const firebaseEnabled = firebaseAdmin.isFirebaseEnabled ? firebaseAdmin.isFirebaseEnabled() : true;
    if (!firebaseEnabled) return;
    const db = admin.firestore();
    if (!db) return;

    const notificationType = payload?.data?.type || extraData.type || 'GENERIC';
    await db.collection('users')
      .doc(String(userId))
      .collection('notification_logs')
      .add({
        userId: String(userId),
        type: notificationType,
        title: payload?.notification?.title || payload?.title || '',
        body: payload?.notification?.body || payload?.body || '',
        data: payload?.data || {},
        status: status || 'SENT',
        productId: extraData.productId || payload?.data?.productId || null,
        orderId: extraData.orderId || payload?.data?.orderId || null,
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: new Date().toISOString()
      });
  } catch (err) {
    console.error('[FCM_SERVICE] Error logging notification:', err.message);
  }
}

/**
 * Check if a notification can be sent based on cooldown rules
 * @param {string} userId
 * @param {string} type - Notification type e.g., RESTOCK_ALERT, SKIN_AWARE_TIP
 * @param {object} options - { productId, cooldownHours }
 */
async function canSendNotification(userId, type, options = {}) {
  if (!userId) return false;
  const { productId, cooldownHours = 24 } = options;

  try {
    const firebaseEnabled = firebaseAdmin.isFirebaseEnabled ? firebaseAdmin.isFirebaseEnabled() : true;
    if (!firebaseEnabled) return true;
    const db = admin.firestore();
    if (!db) return true;

    const cutoff = new Date(Date.now() - cooldownHours * 60 * 60 * 1000);

    let query = db.collection('users')
      .doc(String(userId))
      .collection('notification_logs')
      .where('type', '==', type);

    const snapshot = await query.get();
    if (snapshot.empty) return true;

    for (const doc of snapshot.docs) {
      const data = doc.data();
      let sentTime = null;
      if (data.sentAt && data.sentAt.toDate) {
        sentTime = data.sentAt.toDate();
      } else if (data.createdAt) {
        sentTime = new Date(data.createdAt);
      }

      if (sentTime && sentTime >= cutoff) {
        if (productId) {
          if (String(data.productId) === String(productId)) {
            return false; // Cooldown active for this specific product
          }
        } else {
          return false; // Cooldown active for this notification type
        }
      }
    }

    return true;
  } catch (err) {
    console.error('[FCM_SERVICE] Error checking notification cooldown:', err.message);
    return true; // Fallback to allowing notification if check fails
  }
}

/**
 * Send FCM notification to specific token(s)
 */
async function sendToToken(tokens, payload) {
  try {
    const tokenList = Array.isArray(tokens) ? tokens : [tokens];
    return await firebaseAdmin.sendPushToTokens(tokenList, payload);
  } catch (err) {
    console.error('[FCM_SERVICE] Error in sendToToken:', err.message);
    return { successCount: 0, failureCount: 0, error: err.message };
  }
}

/**
 * Send FCM notification to a specific user by userId
 */
async function sendToUser(userId, payload, extraLogData = {}) {
  if (!userId) return { skipped: true, reason: 'userId is required' };

  try {
    let activeTokens = [];

    // 1. Check Mongo User
    try {
      const mongoUser = await User.findById(userId);
      if (mongoUser && mongoUser.fcmTokens) {
        activeTokens = mongoUser.fcmTokens
          .filter(t => t.active !== false)
          .map(t => t.token || t);
      }
    } catch (e) {}

    // 2. Check Firestore User if no tokens in Mongo
    if (activeTokens.length === 0) {
      try {
        const firebaseEnabled = firebaseAdmin.isFirebaseEnabled ? firebaseAdmin.isFirebaseEnabled() : true;
        if (firebaseEnabled) {
          const db = admin.firestore();
          const userDoc = await db.collection('users').doc(String(userId)).get();
          if (userDoc.exists && userDoc.data().fcmTokens) {
            activeTokens = userDoc.data().fcmTokens
              .filter(t => t.active !== false)
              .map(t => t.token || t);
          }
        }
      } catch (e) {}
    }

    if (activeTokens.length === 0) {
      console.log(`[FCM_SERVICE] Skipping push for user ${userId}: No active FCM tokens found.`);
      return { skipped: true, reason: 'No active FCM tokens' };
    }

    const res = await sendToToken(activeTokens, payload);
    const status = res.successCount > 0 ? 'SENT' : 'FAILED';
    await logNotification(userId, payload, status, extraLogData);
    return res;
  } catch (err) {
    console.error(`[FCM_SERVICE] Error sending push to user ${userId}:`, err.message);
    await logNotification(userId, payload, 'ERROR', extraLogData);
    return { skipped: true, error: err.message };
  }
}

module.exports = {
  sendToToken,
  sendToUser,
  logNotification,
  canSendNotification
};
