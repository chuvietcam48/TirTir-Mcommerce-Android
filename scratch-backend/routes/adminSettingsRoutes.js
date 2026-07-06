const express = require('express');
const router = express.Router();
const { protect, authorize } = require('../middleware/auth');
const {
    updatePreferences,
    changePassword,
    getLoginHistory,
    getApiLogs,
    getAuditTrails
} = require('../controllers/adminSettingsController');

// All settings routes require admin privileges
router.use(protect);
router.use(authorize('admin'));

router.put('/preferences', updatePreferences);
router.put('/change-password', changePassword);
router.get('/login-history', getLoginHistory);
router.get('/api-logs', getApiLogs);
router.get('/audit-trails', getAuditTrails);

module.exports = router;
