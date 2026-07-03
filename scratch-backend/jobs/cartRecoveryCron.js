const cron = require('node-cron');
const admin = require('firebase-admin');
const Cart = require('../models/Cart');
const User = require('../models/User');

const startCartRecoveryCron = () => {
  // Run every hour
  cron.schedule('0 * * * *', async () => {
    console.log('[CRON] Running Cart Recovery Cron Job...');
    try {
      // Find carts that have items and haven't been updated in 2 hours
      const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000);
      const activeCarts = await Cart.find({
        updatedAt: { $lt: twoHoursAgo },
        items: { $not: { $size: 0 } }
      });

      for (const cart of activeCarts) {
        // Here we would normally send an FCM notification to the user's device token
        // Example logic:
        // const user = await User.findById(cart.userId);
        // if (user && user.fcmToken) {
        //   await admin.messaging().send({
        //     token: user.fcmToken,
        //     notification: {
        //       title: 'Giỏ hàng của bạn đang chờ!',
        //       body: 'Bạn đã để quên một số sản phẩm trong giỏ hàng. Nhấp để thanh toán ngay!'
        //     }
        //   });
        // }
        console.log(`[CRON] Detected abandoned cart for user ${cart.userId}`);
      }
    } catch (error) {
      console.error('[CRON] Error running cart recovery job:', error);
    }
  });
};

module.exports = { startCartRecoveryCron };
