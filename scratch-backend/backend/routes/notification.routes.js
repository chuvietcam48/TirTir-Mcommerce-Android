const express = require('express');
const {
    getMyNotifications,
    markAsRead,
    markAllAsRead,
    deleteNotification
} = require('../controllers/notification.controller');

const router = express.Router();

const { protect } = require('../middlewares/auth');

router.use(protect); // All routes are protected

router
    .route('/')
    .get(getMyNotifications);

router
    .route('/read-all')
    .put(markAllAsRead);

router
    .route('/:id/read')
    .put(markAsRead);

router
    .route('/:id')
    .delete(deleteNotification);

module.exports = router;
