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
    getOrderStats
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

// ─── Review Moderation ───────────────────────────────────
// GET  /api/v1/admin/reviews        List all reviews (paginated, ?rating=)
router.get('/reviews', getAllReviewsAdmin);
// DELETE /api/v1/admin/reviews/:id  Remove any review
router.delete('/reviews/:id', deleteReview);

module.exports = router;

