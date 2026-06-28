const admin = require('firebase-admin');
const Wishlist = require('../models/Wishlist');
const Product = require('../models/Product');
const User = require('../models/User');

/**
 * Check and send restock alerts when product stock transitions from 0 to > 0
 */
async function checkAndSendRestockAlert(productId, oldStock, newStock) {
  if (oldStock !== 0 || newStock <= 0) return;

  try {
    const product = await Product.findOne({
      $or: [{ Product_ID: productId }, { _id: productId }]
    });

    if (!product) return;

    const productName = product.Name;
    const pIdStr = String(product._id);

    // Query wishlist users
    const wishlists = await Wishlist.find({
      products: { $in: [productId, pIdStr, product.Product_ID].filter(Boolean) }
    }).lean();

    if (wishlists.length === 0) return;

    const todayStr = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
    const db = admin.firestore();

    for (const item of wishlists) {
      const userId = String(item.userId);

      // Check daily rate limit in Firestore restock_alerts collection
      const alertDocRef = db.collection('restock_alerts').doc(`${userId}_${pIdStr}_${todayStr}`);
      const alertDoc = await alertDocRef.get();

      if (alertDoc.exists) {
        console.log(`[RESTOCK_ALERT] Rate limit reached: Notification already sent to user ${userId} for product ${pIdStr} today.`);
        continue;
      }

      // Fetch FCM tokens
      let tokens = [];
      try {
        const mongoUser = await User.findById(userId);
        if (mongoUser && mongoUser.fcmTokens) {
          tokens = mongoUser.fcmTokens.filter(t => t.active !== false).map(t => t.token || t);
        }
      } catch (e) {}

      if (tokens.length === 0) {
        try {
          const userDoc = await db.collection('users').doc(userId).get();
          if (userDoc.exists && userDoc.data().fcmTokens) {
            tokens = userDoc.data().fcmTokens.filter(t => t.active !== false).map(t => t.token || t);
          }
        } catch (e) {}
      }

      if (tokens.length === 0) continue;

      const messagePayload = {
        notification: {
          title: 'Sản phẩm đã có hàng trở lại!',
          body: `Sản phẩm ${productName} trong danh sách yêu thích của bạn đã có hàng trở lại.`
        },
        data: {
          screen: 'product_detail',
          productId: pIdStr
        }
      };

      try {
        const response = await admin.messaging().sendMulticast({
          tokens,
          ...messagePayload
        });

        console.log(`[RESTOCK_ALERT] Push sent to user ${userId}. Success: ${response.successCount}`);

        // Record alert log to enforce 1 per day limit
        await alertDocRef.set({
          userId,
          productId: pIdStr,
          date: todayStr,
          sentAt: admin.firestore.FieldValue.serverTimestamp()
        });
      } catch (pushErr) {
        console.error(`[RESTOCK_ALERT] Error sending push to user ${userId}:`, pushErr.message);
      }
    }
  } catch (err) {
    console.error('[RESTOCK_ALERT] Service error:', err.message);
  }
}

module.exports = {
  checkAndSendRestockAlert
};
