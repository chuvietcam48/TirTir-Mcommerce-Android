const express = require('express');
const router = express.Router();
const { protect, restrictTo } = require('../middleware/authMiddleware');
const {
  getOverview,
  getTopProducts,
  getMetrics,
  getAdminOrders,
} = require('../controllers/adminController');

// All admin routes must be protected and restricted to 'admin'
router.use(protect);
router.use(restrictTo('admin'));

// GET /api/v1/admin/dashboard/overview
router.get('/dashboard/overview', getOverview);

// GET /api/v1/admin/dashboard/top-products
router.get('/dashboard/top-products', getTopProducts);

// GET /api/v1/admin/metrics
router.get('/metrics', getMetrics);

// GET /api/v1/admin/orders
router.get('/orders', getAdminOrders);

module.exports = router;
