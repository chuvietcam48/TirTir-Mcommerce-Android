const express = require('express');
const router = express.Router();
const { protect, authorize } = require('../middlewares/auth');
const {
    getStats,
    getOverview,
    getRevenueChart,
    getTopProducts,
    getCustomerStats,
    getAllOrders,
    getOrderStats,
    updateOrderStatus,
    getMetrics
} = require('../controllers/admin.dashboard.controller');
const { getAllReviewsAdmin, deleteReview } = require('../controllers/review.controller');

// All routes are protected and require admin role
router.use(protect);
router.use(authorize('admin'));

// GET /api/admin/dashboard/stats
router.get('/dashboard/stats', getStats);

// GET /api/admin/dashboard/overview
router.get('/dashboard/overview', getOverview);

// GET /api/admin/dashboard/revenue
router.get('/dashboard/revenue', getRevenueChart);

// GET /api/admin/dashboard/top-products
router.get('/dashboard/top-products', getTopProducts);

// GET /api/admin/dashboard/customers
router.get('/dashboard/customers', getCustomerStats);

// GET /api/admin/orders/stats
router.get('/orders/stats', getOrderStats);

// GET /api/admin/orders  (supports ?status=&search=&startDate=&endDate=)
router.get('/orders', getAllOrders);

// PATCH /api/admin/orders/:id/status — update order status + sync Firestore
router.patch('/orders/:id/status', updateOrderStatus);

// GET /api/admin/metrics?range=7d|30d|3m — time-series revenue + order count
router.get('/metrics', getMetrics);

// ─── Marketing Overview ──────────────────────────────────
const { getMarketingOverview } = require('../controllers/marketing.controller');
router.get('/marketing/overview', getMarketingOverview);

// ─── Review Moderation ───────────────────────────────────
// GET  /api/v1/admin/reviews        List all reviews (paginated, ?rating=)
router.get('/reviews', getAllReviewsAdmin);
// DELETE /api/v1/admin/reviews/:id  Remove any review
router.delete('/reviews/:id', deleteReview);

// POST /api/v1/admin/notifications/send-voucher-fcm
router.post('/notifications/send-voucher-fcm', async (req, res, next) => {
    try {
        const { userId, voucherCode, discountPct, expiryDays } = req.body;
        if (!userId || !voucherCode) {
            return res.status(400).json({ success: false, message: 'Missing userId or voucherCode' });
        }

        const { sendVoucherFcmToUser } = require('../services/voucherFcm.service');
        const result = await sendVoucherFcmToUser(userId, {
            voucherCode,
            discountPct: discountPct || 10,
            expiryDays: expiryDays || 7
        });

        res.status(200).json({ success: true, result });
    } catch (err) {
        next(err);
    }
});

// Demo: Trigger Cart Recovery Scan
router.post('/demo/trigger-cart-recovery', async (req, res, next) => {
    try {
        const { runCartRecovery } = require('../services/cartRecoveryFcm.service');
        const stats = await runCartRecovery();
        res.status(200).json({ success: true, message: 'Cart recovery job completed', data: stats });
    } catch (err) {
        next(err);
    }
});

// Demo: Trigger Restock Alert Push
router.post('/demo/restock-alert', async (req, res, next) => {
    try {
        const { productId, productName } = req.body;
        const Product = require('../models/product.model');
        const User = require('../models/user.model');
        const firebaseAdmin = require('../services/firebaseAdmin.service');

        let prodName = productName || 'Sản phẩm TirTir';
        let prodId = productId;

        if (!prodId) {
            const p = await Product.findOne().select('_id Name');
            if (p) {
                prodId = String(p._id);
                prodName = p.Name;
            } else {
                prodId = 'mock_product_id_123';
            }
        }

        const users = await User.find({ "fcmTokens.0": { $exists: true } });
        let sentCount = 0;

        for (const user of users) {
            const activeTokens = user.fcmTokens.filter(t => t.active !== false).map(t => t.token);
            if (activeTokens.length > 0) {
                await firebaseAdmin.sendPushToTokens(activeTokens, {
                    title: "Sản phẩm yêu thích đã có hàng! 🎉",
                    body: `Sản phẩm ${prodName} của bạn quan tâm đã được restock. Hãy nhanh tay đặt mua ngay kẻo hết!`,
                    data: {
                        type: "restock_alert",
                        screen: "product_detail",
                        productId: String(prodId)
                    }
                });
                sentCount++;
            }
        }

        res.status(200).json({
            success: true,
            message: `Restock alerts sent successfully to ${sentCount} users`,
            productId: prodId,
            productName: prodName
        });
    } catch (err) {
        next(err);
    }
});

// Demo: Trigger Weekly Skin Tips Push
router.post('/demo/weekly-skin-tip', async (req, res, next) => {
    try {
        const { sendWeeklySkinTips } = require('../cron/fcmSkinTips.cron');
        const stats = await sendWeeklySkinTips();
        res.status(200).json({ success: true, message: 'Weekly skin tips processing completed', data: stats });
    } catch (err) {
        next(err);
    }
});

module.exports = router;

