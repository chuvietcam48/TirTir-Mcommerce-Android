const Wishlist = require('../models/Wishlist');
const Product = require('../models/Product');
const fcmService = require('./fcmService');

/**
 * Check and send restock alerts when product stock transitions from 0 to > 0
 */
async function checkAndSendRestockAlert(productId, oldStock, newStock) {
  // Only trigger when stock transitions from 0 to > 0
  if (oldStock !== 0 || newStock <= 0) return;

  try {
    const product = await Product.findOne({
      $or: [{ Product_ID: productId }, { _id: productId }]
    });

    if (!product) return;

    const productName = product.Name;
    const pIdStr = String(product.Product_ID || product._id);

    // Query wishlist users
    const wishlists = await Wishlist.find({
      products: { $in: [productId, pIdStr, product.Product_ID, String(product._id)].filter(Boolean) }
    }).lean();

    if (!wishlists || wishlists.length === 0) return;

    for (const item of wishlists) {
      const userId = String(item.userId);

      // 24-hour rate limit per user per product
      const canSend = await fcmService.canSendNotification(userId, 'RESTOCK_ALERT', {
        productId: pIdStr,
        cooldownHours: 24
      });

      if (!canSend) {
        console.log(`[RESTOCK_ALERT] Cooldown active for user ${userId} on product ${pIdStr}. Skipping.`);
        continue;
      }

      const payload = {
        notification: {
          title: 'Sản phẩm đã có hàng lại!',
          body: `${productName} bạn quan tâm đã được restock.`
        },
        data: {
          screen: 'PRODUCT_DETAIL',
          productId: pIdStr,
          type: 'RESTOCK_ALERT'
        }
      };

      await fcmService.sendToUser(userId, payload, { productId: pIdStr, type: 'RESTOCK_ALERT' });
    }
  } catch (err) {
    console.error('[RESTOCK_ALERT] Service error:', err.message);
  }
}

module.exports = {
  checkAndSendRestockAlert
};

