const express = require('express');
const router = express.Router();
const { registerFcmToken, getMyNotifications, markAsRead, markAllAsRead } = require('../backend/controllers/notification.controller');
const { protect } = require('../middleware/authMiddleware');

router.get('/', protect, getMyNotifications);
router.post('/fcm-token', protect, registerFcmToken);
router.put('/:id/read', protect, markAsRead);
router.put('/read-all', protect, markAllAsRead);

module.exports = router;
