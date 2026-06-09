const Notification = require('../models/notification.model');
const User = require('../models/user.model');
const firebaseAdmin = require('../services/firebaseAdmin.service');
const ErrorResponse = require('../utils/errorResponse');

// @desc    Get my notifications
// @route   GET /api/v1/notifications
// @access  Private
exports.getMyNotifications = async (req, res, next) => {
    try {
        const notifications = await Notification.find({ user: req.user.id })
            .sort({ createdAt: -1 });

        res.status(200).json({
            success: true,
            count: notifications.length,
            data: notifications
        });
    } catch (err) {
        next(err);
    }
};

// @desc    Mark notification as read
// @route   PUT /api/v1/notifications/:id/read
// @access  Private
exports.markAsRead = async (req, res, next) => {
    try {
        let notification = await Notification.findById(req.params.id);

        if (!notification) {
            return next(new ErrorResponse(`Notification not found with id of ${req.params.id}`, 404));
        }

        // Make sure user owns the notification
        if (notification.user.toString() !== req.user.id) {
            return next(new ErrorResponse(`User not authorized to update this notification`, 401));
        }

        notification = await Notification.findByIdAndUpdate(
            req.params.id,
            { isRead: true },
            { new: true, runValidators: true }
        );

        res.status(200).json({
            success: true,
            data: notification
        });
    } catch (err) {
        next(err);
    }
};

// @desc    Mark all notifications as read
// @route   PUT /api/v1/notifications/read-all
// @access  Private
exports.markAllAsRead = async (req, res, next) => {
    try {
        await Notification.updateMany(
            { user: req.user.id, isRead: false },
            { isRead: true }
        );

        res.status(200).json({
            success: true,
            message: 'All notifications marked as read'
        });
    } catch (err) {
        next(err);
    }
};

// @desc    Delete notification
// @route   DELETE /api/v1/notifications/:id
// @access  Private
exports.deleteNotification = async (req, res, next) => {
    try {
        const notification = await Notification.findById(req.params.id);

        if (!notification) {
            return next(new ErrorResponse(`Notification not found with id of ${req.params.id}`, 404));
        }

        // Make sure user owns the notification
        if (notification.user.toString() !== req.user.id) {
            return next(new ErrorResponse(`User not authorized to delete this notification`, 401));
        }

        await notification.deleteOne();

        res.status(200).json({
            success: true,
            data: {}
        });
    } catch (err) {
        next(err);
    }
};

// Utility Function to Create Notification (Internal Use)
exports.createNotification = async (userId, type, title, message, link, image) => {
    try {
        const notification = await Notification.create({
            user: userId,
            type,
            title,
            message,
            link,
            image
        });

        // Gửi push notification thông qua FCM bất đồng bộ nếu Firebase đã cấu hình
        if (firebaseAdmin.isFirebaseEnabled()) {
            firebaseAdmin.sendPushToUser(userId, {
                title,
                body: message,
                data: {
                    type: type || 'system',
                    link: link || '',
                    notificationId: String(notification._id)
                }
            }).catch(pushErr => {
                console.error('FCM Push Notification failed inside createNotification:', pushErr.message);
            });
        }

        return notification;
    } catch (err) {
        console.error('Error creating notification:', err);
        // We explicitly do NOT throw here to prevent blocking the main flow (e.g. order creation)
        return null;
    }
};

// @desc    Register FCM Token
// @route   POST /api/v1/notifications/fcm-token
// @access  Private
exports.registerFcmToken = async (req, res, next) => {
    try {
        const userId = req.user.id;
        const { token, platform, firebaseUid, deviceModel, appVersion } = req.body;

        if (!token) {
            return next(new ErrorResponse('Please provide an FCM token', 400));
        }

        const user = await User.findById(userId);
        if (!user) {
            return next(new ErrorResponse('User not found', 404));
        }

        if (!user.fcmTokens) {
            user.fcmTokens = [];
        }

        const tokenIndex = user.fcmTokens.findIndex(item => item.token === token);

        if (tokenIndex > -1) {
            user.fcmTokens[tokenIndex].active = true;
            user.fcmTokens[tokenIndex].lastSeenAt = new Date();
            if (firebaseUid) user.fcmTokens[tokenIndex].firebaseUid = firebaseUid;
            if (platform) user.fcmTokens[tokenIndex].platform = platform;
            if (deviceModel) user.fcmTokens[tokenIndex].deviceModel = deviceModel;
            if (appVersion) user.fcmTokens[tokenIndex].appVersion = appVersion;
        } else {
            user.fcmTokens.push({
                token,
                platform: platform || 'android',
                firebaseUid,
                deviceModel,
                appVersion,
                active: true,
                lastSeenAt: new Date(),
                createdAt: new Date()
            });
        }

        await user.save();

        res.status(200).json({
            success: true,
            message: 'FCM token registered successfully'
        });
    } catch (err) {
        next(err);
    }
};

// @desc    Send test push notification to user
// @route   POST /api/v1/notifications/test-push
// @access  Private
exports.sendTestPush = async (req, res, next) => {
    try {
        const userId = req.user.id;
        
        if (!firebaseAdmin.isFirebaseEnabled()) {
            return res.status(503).json({
                success: false,
                message: 'Firebase push service is not configured/enabled on the server.'
            });
        }

        const result = await firebaseAdmin.sendTestPush(userId);

        res.status(200).json({
            success: true,
            data: result
        });
    } catch (err) {
        next(err);
    }
};
