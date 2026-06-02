const express = require('express');
const router = express.Router();
const { protect, authorize } = require('../middlewares/auth');
const statsController = require('../controllers/admin.stats.controller');

router.get('/revenue', protect, authorize('admin'), statsController.getRevenueChart);
router.get('/conversion', protect, authorize('admin'), statsController.getConversionFunnel);
router.get('/top-products', protect, authorize('admin'), statsController.getTopProducts);
router.get('/inventory', protect, authorize('admin'), statsController.getInventoryForecast);
router.get('/ai-insights', protect, authorize('admin'), statsController.getAiInsights);
router.get('/cart-recovery', protect, authorize('admin'), statsController.getCartRecoveryStats);

module.exports = router;
