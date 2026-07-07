const express = require('express');
const router = express.Router();
const { protect, restrictTo } = require('../middleware/authMiddleware');
const {
  getOverview,
  getTopProducts,
  getMetrics,
  createCampaign,
  getAdminVoucherStats,
  getAdminVouchers,
  getAdminVoucherById,
  createAdminVoucher,
  updateAdminVoucher,
  deleteAdminVoucher,
  getMarketingOverview,
  getRetentionAnalytics,
  getAtRiskUsers
} = require('../controllers/adminController');
const {
  getAdminOrders,
  getAdminOrderDetails,
  updateAdminNotes,
  updateShippingDetails,
  cancelOrderAdmin,
  exportOrdersCsv
} = require('../controllers/orderController');

// POST /api/v1/admin/marketing/seed
router.post('/marketing/seed', require('../controllers/adminController').seedMarketingData);

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

// GET /api/v1/admin/orders/export/csv
router.get('/orders/export/csv', exportOrdersCsv);

// GET /api/v1/admin/orders/:id
router.get('/orders/:id', getAdminOrderDetails);

// PATCH /api/v1/admin/orders/:id/notes
router.patch('/orders/:id/notes', updateAdminNotes);

// PATCH /api/v1/admin/orders/:id/shipping
router.patch('/orders/:id/shipping', updateShippingDetails);

// POST /api/v1/admin/orders/:id/cancel
router.post('/orders/:id/cancel', cancelOrderAdmin);

// GET /api/v1/admin/marketing/overview
router.get('/marketing/overview', getMarketingOverview);

// POST /api/v1/admin/marketing/campaigns
router.post('/marketing/campaigns', createCampaign);

const { getCartRecoveryStats, getChurnList, getAbandonedCarts, sendVoucher } = require('../controllers/adminStatsController');

// GET /api/v1/admin/stats/cart-recovery
router.get('/stats/cart-recovery', getCartRecoveryStats);

// GET /api/v1/admin/churn
router.get('/churn', getChurnList);

// GET /api/v1/admin/churn/abandoned-carts
router.get('/churn/abandoned-carts', getAbandonedCarts);

// POST /api/v1/admin/churn/send-voucher
router.post('/churn/send-voucher', sendVoucher);

// GET /api/v1/admin/vouchers/stats
router.get('/vouchers/stats', getAdminVoucherStats);

// GET /api/v1/admin/vouchers
router.get('/vouchers', getAdminVouchers);

// GET /api/v1/admin/vouchers/:id
router.get('/vouchers/:id', getAdminVoucherById);

// POST /api/v1/admin/vouchers
router.post('/vouchers', createAdminVoucher);

// PUT /api/v1/admin/vouchers/:id
router.put('/vouchers/:id', updateAdminVoucher);

// DELETE /api/v1/admin/vouchers/:id
router.delete('/vouchers/:id', deleteAdminVoucher);

// GET /api/v1/admin/retention/stats
router.get('/retention/stats', getRetentionAnalytics);

// GET /api/v1/admin/retention/users
router.get('/retention/users', getAtRiskUsers);

module.exports = router;
