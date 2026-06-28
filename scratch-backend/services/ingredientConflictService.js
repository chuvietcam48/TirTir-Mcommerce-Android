const Order = require('../backend/models/order.model');
const Product = require('../backend/models/product.model');
const { findConflicts } = require('../backend/controllers/ingredient.controller');
const fcmService = require('./fcmService');

/**
 * Handle ingredient conflict check & FCM push after order confirmation
 */
async function handleOrderConfirmedIngredientAlert(orderIdOrDoc) {
  try {
    let order = orderIdOrDoc;
    if (typeof orderIdOrDoc === 'string' || (orderIdOrDoc && orderIdOrDoc._id)) {
      if (typeof orderIdOrDoc === 'string' || typeof orderIdOrDoc === 'object') {
        const idToFind = orderIdOrDoc._id || orderIdOrDoc;
        order = await Order.findById(idToFind).populate('items.product');
      }
    }

    if (!order || !order.user || !order.items || order.items.length < 2) {
      return { skipped: true, reason: 'Insufficient items for conflict check' };
    }

    const userId = String(order.user._id || order.user);
    const orderIdStr = String(order._id);

    // Rate limit check: avoid duplicate alert for same order
    const canSend = await fcmService.canSendNotification(userId, 'INGREDIENT_CONFLICT', {
      orderId: orderIdStr,
      cooldownHours: 168 // 7 days
    });

    if (!canSend) {
      console.log(`[INGREDIENT_ALERT] Notification already sent for order ${orderIdStr}. Skipping.`);
      return { skipped: true, reason: 'Already notified for this order' };
    }

    // Extract product ingredients per item
    const productIngredients = [];
    for (const item of order.items) {
      let prod = item.product;
      if (typeof prod === 'string' || (prod && !prod.Name)) {
        prod = await Product.findById(prod);
      }
      if (!prod) continue;

      const name = prod.Name || item.name || 'Sản phẩm';
      const rawIng = prod.ingredients || prod.Key_Ingredients || prod.Full_Description || '';
      let ingList = [];
      if (Array.isArray(rawIng)) {
        ingList = rawIng;
      } else if (typeof rawIng === 'string') {
        ingList = rawIng.split(',').map(s => s.trim());
      }

      if (ingList.length > 0) {
        productIngredients.push({ name, ingredients: ingList });
      }
    }

    if (productIngredients.length < 2) {
      return { skipped: true, reason: 'Not enough products with ingredient data' };
    }

    // Check conflicts across all item pairs
    let detectedConflict = null;
    for (let i = 0; i < productIngredients.length; i++) {
      for (let j = i + 1; j < productIngredients.length; j++) {
        const p1 = productIngredients[i];
        const p2 = productIngredients[j];
        const conflicts = findConflicts(p1.ingredients, p2.ingredients);
        if (conflicts && conflicts.length > 0) {
          detectedConflict = {
            productA: p1.name,
            productB: p2.name,
            conflict: conflicts[0]
          };
          break;
        }
      }
      if (detectedConflict) break;
    }

    if (!detectedConflict) {
      console.log(`[INGREDIENT_ALERT] No ingredient conflicts found in order ${orderIdStr}.`);
      return { skipped: true, reason: 'No conflicts found' };
    }

    const { productA, productB } = detectedConflict;
    const payload = {
      notification: {
        title: 'Lưu ý khi dùng sản phẩm mới',
        body: `${productA} và ${productB} nên dùng cách nhau sáng/tối để giảm kích ứng.`
      },
      data: {
        screen: 'INGREDIENT_WARNING',
        orderId: orderIdStr,
        type: 'INGREDIENT_CONFLICT'
      }
    };

    console.log(`[INGREDIENT_ALERT] Detected conflict between ${productA} and ${productB} in order ${orderIdStr}. Sending push to user ${userId}...`);
    return await fcmService.sendToUser(userId, payload, { orderId: orderIdStr, type: 'INGREDIENT_CONFLICT' });

  } catch (err) {
    console.error('[INGREDIENT_ALERT] Error handling ingredient alert:', err.message);
    return { skipped: true, error: err.message };
  }
}

module.exports = {
  handleOrderConfirmedIngredientAlert
};
