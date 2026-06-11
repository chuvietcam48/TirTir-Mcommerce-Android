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

module.exports = router;

