const Notification = require('../models/notification.model');
const ErrorResponse = require('../utils/errorResponse'); // Assuming this exists based on other controllers

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
        return notification;
    } catch (err) {
        console.error('Error creating notification:', err);
        // We explicitly do NOT throw here to prevent blocking the main flow (e.g. order creation)
        return null;
    }
};
