const express = require('express');
const router = express.Router();
const inventoryController = require('../controllers/inventory.controller');
const { protect, authorize } = require('../middlewares/auth');

// Public/Client Routes
router.post('/validate-cart', inventoryController.validateCart);

// Admin & Inventory Staff Routes
router.get('/stats', protect, authorize('admin', 'inventory_staff'), inventoryController.getInventoryStats);
router.get('/alerts', protect, authorize('admin', 'inventory_staff'), inventoryController.getInventoryAlerts);
router.get('/logs', protect, authorize('admin', 'inventory_staff'), inventoryController.getStockLogs);
router.patch('/adjust', protect, authorize('admin', 'inventory_staff'), inventoryController.adjustStock);
router.post('/cleanup', protect, authorize('admin', 'inventory_staff'), inventoryController.cleanupPendingOrders);

module.exports = router;
