const express = require('express');
const router = express.Router();
const settingController = require('../controllers/setting.controller');
const { protect, authorize } = require('../middlewares/auth');

// Public routes (if needed for storefront)
router.get('/public', settingController.getPublicSettings);

// Admin routes
router.get('/', protect, authorize('admin'), settingController.getSettings);
router.put('/', protect, authorize('admin'), settingController.updateSettings);

module.exports = router;
