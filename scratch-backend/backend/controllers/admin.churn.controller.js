const User = require('../models/user.model');
const Order = require('../models/order.model');
const Voucher = require('../models/voucher.model');
const voucherFcmService = require('../services/voucherFcm.service');
const crypto = require('crypto');

/**
 * @desc    Get RFM Churn list for users
 * @route   GET /api/v1/admin/churn
 * @access  Private/Admin
 */
exports.getChurnList = async (req, res, next) => {
    try {
        const users = await User.find({ role: 'user' }).select('name email phone totalOrders loyaltyTier avatar');
        const now = new Date();
        const results = [];

        for (const user of users) {
            // Find completed orders for this user
            const orders = await Order.find({ user: user._id, status: { $nin: ['Cancelled'] } }).sort({ createdAt: -1 });
            
            let recency = 999; // Default large number for users without orders
            let frequency = orders.length;
            let monetary = 0;

            if (orders.length > 0) {
                const lastOrderDate = new Date(orders[0].createdAt);
                recency = Math.floor((now - lastOrderDate) / (1000 * 60 * 60 * 24));
                monetary = orders.reduce((sum, order) => sum + (order.totalAmount || 0), 0);
            }

            let classification = 'New';
            if (frequency <= 1) {
                classification = 'New';
            } else if (recency > 60) {
                classification = 'Churned';
            } else if (recency > 30) {
                classification = 'At Risk';
            } else if (recency <= 7 && frequency >= 5) {
                classification = 'Champion';
            } else if (recency <= 14 && frequency >= 3) {
                classification = 'Loyal';
            } else {
                classification = 'Regular';
            }

            results.push({
                user,
                rfm: { recency: recency === 999 ? null : recency, frequency, monetary },
                classification
            });
        }

        res.status(200).json({ success: true, count: results.length, data: results });
    } catch (error) {
        next(error);
    }
};

/**
 * @desc    Send manual voucher to user
 * @route   POST /api/v1/admin/churn/send-voucher
 * @access  Private/Admin
 */
exports.sendManualVoucher = async (req, res, next) => {
    try {
        const { userId, discountPct, expiryDays } = req.body;
        
        if (!userId || !discountPct || !expiryDays) {
            return res.status(400).json({ success: false, message: 'Vui lòng cung cấp đủ userId, discountPct và expiryDays.' });
        }

        // Check if user exists
        const user = await User.findById(userId);
        if (!user) {
            return res.status(404).json({ success: false, message: 'Không tìm thấy User' });
        }

        // Generate 4-char hex string -> TIRTIR-XXXX
        const randomString = crypto.randomBytes(2).toString('hex').toUpperCase();
        const code = `TIRTIR-${randomString}`;
        
        const validTo = new Date();
        validTo.setDate(validTo.getDate() + Number(expiryDays));

        const voucher = await Voucher.create({
            code,
            userId,
            discountPct,
            validTo,
            source: 'Admin'
        });

        // Fire and forget FCM sending
        const fcmResult = await voucherFcmService.sendVoucherFcmToUser(userId, {
            voucherCode: code,
            discountPct,
            validTo
        });

        res.status(201).json({ 
            success: true, 
            data: voucher,
            fcmSent: fcmResult.success,
            fcmWarning: fcmResult.warning
        });
    } catch (error) {
        next(error);
    }
};
