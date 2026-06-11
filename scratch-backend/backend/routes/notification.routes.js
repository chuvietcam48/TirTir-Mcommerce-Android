const express = require('express');
const {
    getMyNotifications,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    registerFcmToken,
    sendTestPush
} = require('../controllers/notification.controller');

const router = express.Router();

const { protect } = require('../middlewares/auth');

router.use(protect); // All routes are protected

router.post('/fcm-token', registerFcmToken);
router.post('/test-push', sendTestPush);

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

if (process.env.NODE_ENV !== 'production') {
    router.post('/dev/run-cart-recovery', async (req, res, next) => {
        try {
            const { runCartRecovery } = require('../services/cartRecoveryFcm.service');
            const stats = await runCartRecovery();
            res.status(200).json({ success: true, stats });
        } catch (err) {
            next(err);
        }
    });
}

module.exports = router;
