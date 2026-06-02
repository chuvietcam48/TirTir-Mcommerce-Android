const User = require('../models/user.model');
const Cart = require('../models/cart.model');
const { createNotification } = require('./notification.controller');

/**
 * @desc    Send Flash Sale Notification (Bulk)
 * @route   POST /api/v1/marketing/flash-sale
 * @access  Private (Admin)
 */
exports.sendFlashSale = async (req, res, next) => {
    try {
        const { title, message, link, image } = req.body;

        if (!title || !message) {
            return res.status(400).json({
                success: false,
                message: 'Title and message are required'
            });
        }

        // 1. Get all user IDs (only regular users)
        const userIds = await User.find({ role: 'user' }).distinct('_id');

        if (!userIds.length) {
            return res.status(404).json({
                success: false,
                message: 'No users found to send notification'
            });
        }

        // 2. Build array of notification objects
        const notifications = userIds.map(userId => ({
            user: userId,
            type: 'promotion',
            title,
            message,
            link: link || '/products',
            image,
            isRead: false
        }));

        // 3. Bulk Insert (Using Model directly for performance)
        const Notification = require('../models/notification.model');
        await Notification.insertMany(notifications);

        res.status(200).json({
            success: true,
            message: `Flash sale notification sent to ${userIds.length} users.`
        });

    } catch (err) {
        next(err);
    }
};

/**
 * @desc    Recover Abandoned Carts
 * @route   POST /api/v1/marketing/abandoned-cart-recovery
 * @access  Private (Admin/System)
 */
exports.recoverAbandonedCarts = async (req, res, next) => {
    try {
        // Time Window Logic: 30 minutes < Time < 24 hours
        const now = new Date();
        const thirtyMinsAgo = new Date(now.getTime() - 30 * 60 * 1000);
        const twentyFourHoursAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        // 1. Find Carts
        // - Updated between 24h ago and 30m ago
        // - Has items
        // - Recovery notification NOT sent yet
        const abandonedCarts = await Cart.find({
            updatedAt: { $lt: thirtyMinsAgo, $gt: twentyFourHoursAgo },
            items: { $ne: [] },
            recoveryNotificationSent: { $ne: true }
        }).populate('user', 'name'); // Populate to check if user exists/get info

        if (!abandonedCarts.length) {
            return res.status(200).json({
                success: true,
                message: 'No abandoned carts found in the time window.'
            });
        }

        let sentCount = 0;

        // 2. Loop through carts
        for (const cart of abandonedCarts) {
            if (!cart.user) continue; // Skip if user deleted

            // 3. Create Notification
            await createNotification(
                cart.user._id,
                'system',
                'You left something behind!',
                'Your cart is missing you. Complete your order now and get your beauty essentials!',
                '/cart'
            );

            // 4. CRITICAL: Update Cart Flag
            await Cart.updateOne(
                { _id: cart._id },
                { recoveryNotificationSent: true }
            );

            sentCount++;
        }

        res.status(200).json({
            success: true,
            message: `Abandoned cart recovery run. Sent ${sentCount} notifications.`
        });

    } catch (err) {
        next(err);
    }
};
