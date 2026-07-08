const User = require('../models/user.model');
const Cart = require('../models/cart.model');
const Order = require('../models/order.model');
const Campaign = require('../models/campaign.model');
const MarketingActivity = require('../models/marketing_activity.model');
const { createNotification } = require('./notification.controller');
const { ORDER_STATUS } = require('../constants');

/**
 * @desc    Get Marketing Overview Data
 * @route   GET /api/v1/admin/marketing/overview
 * @access  Private (Admin)
 */
exports.getMarketingOverview = async (req, res, next) => {
    try {
        console.log('[MARKETING] Fetching overview data...');

        // 1. Insights calculation with Safeguards
        let revenueRecovered = 0;
        let atRiskUsers = 0;
        let vouchersUsed = 0;
        let conversionRate = 0;

        try {
            // Revenue Recovered
            const recoveryOrders = await Order.find({
                status: { $ne: ORDER_STATUS.CANCELLED },
                recoveredFrom: { $exists: true }
            });
            revenueRecovered = recoveryOrders.reduce((sum, o) => sum + (o.totalAmount || 0), 0);
            vouchersUsed = recoveryOrders.length;
        } catch (e) { console.error('Insights calculation error (Orders):', e.message); }

        try {
            // At Risk Users (Haven't updated profile in 30 days)
            const thirtyDaysAgo = new Date();
            thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
            atRiskUsers = await User.countDocuments({
                role: 'user',
                updatedAt: { $lt: thirtyDaysAgo }
            });
        } catch (e) { console.error('Insights calculation error (Users):', e.message); }

        try {
            // Conversion Rate
            const totalOrders = await Order.countDocuments({ status: { $ne: ORDER_STATUS.CANCELLED } });
            const totalUsers = await User.countDocuments({ role: 'user' });
            conversionRate = totalUsers > 0 ? Number(((totalOrders / totalUsers) * 100).toFixed(1)) : 0;
        } catch (e) { console.error('Insights calculation error (Conv):', e.message); }

        // 2. Campaigns
        let campaigns = await Campaign.find().sort({ startDate: -1 }).limit(5);

        // Default if empty
        if (campaigns.length === 0) {
            campaigns = [
                {
                    _id: 'camp_demo_1',
                    title: 'Spring Glow Blast (Demo)',
                    status: 'Active',
                    currentRevenue: revenueRecovered || 500000,
                    targetRevenue: 2000000,
                    endDate: new Date(Date.now() + 86400000 * 7).toISOString()
                }
            ];
        }

        // 3. Activities
        let activities = await MarketingActivity.find().sort({ createdAt: -1 }).limit(10);

        if (activities.length === 0) {
            activities = [
                {
                    _id: 'act_demo_1',
                    title: 'Cart Recovery Engine',
                    targetOrStatus: `Active - Processed ${revenueRecovered > 0 ? 'recent' : '0'} items`,
                    type: 'system',
                    createdAt: new Date().toISOString()
                }
            ];
        }

        console.log('[MARKETING] Data compiled successfully.');

        return res.status(200).json({
            success: true,
            data: {
                insights: {
                    revenueRecovered,
                    atRiskUsers,
                    vouchersUsed,
                    conversionRate
                },
                campaigns,
                activities
            }
        });

    } catch (err) {
        console.error('[MARKETING] Critical Controller Error:', err);
        return res.status(500).json({
            success: false,
            message: 'Internal server error in marketing controller: ' + err.message
        });
    }
};

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

        // 4. Send actual Push Notifications via Firebase Admin
        const firebaseAdmin = require('../services/firebaseAdmin.service');
        if (firebaseAdmin.isFirebaseEnabled()) {
            const admin = require('firebase-admin');
            const allUsers = await User.find({ role: 'user', 'fcmTokens.0': { $exists: true } });
            const tokens = [];
            allUsers.forEach(u => {
                u.fcmTokens.forEach(t => {
                    if (t.token) tokens.push(t.token);
                });
            });

            if (tokens.length > 0) {
                // Firebase Admin send() requires a loop now due to /batch deprecation
                for (const token of tokens) {
                    try {
                        await admin.messaging().send({
                            notification: { title, body: message },
                            data: { type: 'PROMOTION', screen: link || '/products' },
                            token: token
                        });
                    } catch (e) {
                        console.error('Error sending flash sale push to token:', token, e.message);
                    }
                }
            }
        }

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
