const mongoose = require('mongoose');
const Order = require('../models/Order');
const Cart = require('../models/Cart');
const User = require('../models/User');
const Product = require('../models/Product');

exports.getCartRecoveryStats = async (req, res) => {
    try {
        const thresholdDate = new Date(Date.now() - 2 * 60 * 60 * 1000);
        
        const totalAbandoned = await Cart.countDocuments({
            'items.0': { $exists: true },
            updatedAt: { $lt: thresholdDate },
            recoveryState: { $ne: 'recovered' }
        });

        const recoveredCount = await Cart.countDocuments({
            recoveryState: 'recovered'
        });

        const totalTracked = totalAbandoned + recoveredCount;
        const conversionRate = totalTracked > 0 ? parseFloat(((recoveredCount / totalTracked) * 100).toFixed(2)) : 0;

        res.json({
            totalAbandoned,
            recoveredCount,
            conversionRate
        });
    } catch (error) {
        console.error('getCartRecoveryStats Error:', error);
        res.status(500).json({ message: error.message });
    }
};

exports.getAbandonedCarts = async (req, res) => {
    try {
        const thresholdDate = new Date(Date.now() - 2 * 60 * 60 * 1000);
        const carts = await Cart.find({
            'items.0': { $exists: true },
            updatedAt: { $lt: thresholdDate },
            recoveryState: { $ne: 'recovered' }
        }).populate('userId', 'firstName lastName email avatar phone');

        const result = [];
        
        for (const cart of carts) {
            if (!cart.userId) continue;
            
            let totalValue = 0;
            const itemsDetails = [];
            
            for (const item of cart.items) {
                let product = await Product.findOne({ Product_ID: item.productId });
                if (!product && mongoose.Types.ObjectId.isValid(item.productId)) {
                    product = await Product.findById(item.productId);
                }
                
                if (product) {
                    const price = product.Sale_Price > 0 ? product.Sale_Price : product.Price;
                    totalValue += price * item.quantity;
                    itemsDetails.push({
                        productId: product.Product_ID || product._id,
                        name: product.Name,
                        quantity: item.quantity,
                        price: price,
                        shade: item.shade,
                        thumbnail: product.Thumbnail_Images
                    });
                }
            }
            
            const lastOrder = await Order.findOne({ userId: cart.userId._id }).sort({ createdAt: -1 });
            let daysSinceLastOrder = -1;
            if (lastOrder) {
                daysSinceLastOrder = Math.floor((Date.now() - new Date(lastOrder.createdAt)) / (1000 * 60 * 60 * 24));
            }

            result.push({
                cartId: cart._id,
                userId: cart.userId._id,
                user: {
                    name: `${cart.userId.firstName || ''} ${cart.userId.lastName || ''}`.trim() || cart.userId.email,
                    email: cart.userId.email,
                    avatar: cart.userId.avatar
                },
                itemsCount: cart.items.length,
                totalValue: totalValue,
                items: itemsDetails,
                lastActivity: cart.updatedAt,
                recoveryState: cart.recoveryState,
                recoveryNotifiedAt: cart.recoveryNotifiedAt,
                daysSinceLastOrder
            });
        }
        
        result.sort((a, b) => b.totalValue - a.totalValue);
        res.json({ success: true, data: result });
    } catch (error) {
        console.error('getAbandonedCarts Error:', error);
        res.status(500).json({ success: false, message: 'Failed to get abandoned carts' });
    }
};

exports.getChurnList = async (req, res) => {
    try {
        const users = await User.find({ role: 'user' });
        const churnList = [];
        const now = new Date();
        
        for (const user of users) {
            const lastOrder = await Order.findOne({ userId: user._id }).sort({ createdAt: -1 });
            let daysSinceLastOrder = 0;
            
            if (lastOrder) {
                daysSinceLastOrder = Math.floor((now - new Date(lastOrder.createdAt)) / (1000 * 60 * 60 * 24));
            } else {
                daysSinceLastOrder = Math.floor((now - new Date(user.createdAt)) / (1000 * 60 * 60 * 24));
            }

            let riskLevel = 'Low';
            let status = 'Loyal';
            
            if (daysSinceLastOrder > 90) {
                riskLevel = 'High';
                status = 'Churned';
            } else if (daysSinceLastOrder > 60) {
                riskLevel = 'Medium';
                status = 'At Risk';
            } else if (!lastOrder) {
                status = 'New';
            }

            churnList.push({
                userId: user._id,
                name: `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email,
                email: user.email,
                daysSinceLastOrder,
                status,
                riskLevel
            });
        }
        
        churnList.sort((a, b) => b.daysSinceLastOrder - a.daysSinceLastOrder);
        res.json({ success: true, data: churnList });
    } catch (error) {
        console.error('getChurnList Error:', error);
        res.status(500).json({ success: false, message: 'Failed to get churn list' });
    }
};

const fcmService = require('../services/fcmService');

exports.sendVoucher = async (req, res) => {
    try {
        const { userId, voucherCode } = req.body;
        
        const cart = await Cart.findOne({ userId });
        if (cart && cart.items.length > 0 && cart.recoveryState === 'pending') {
            cart.recoveryState = 'notified';
            cart.recoveryNotifiedAt = new Date();
            await cart.save();
        }

        // Send actual FCM Push Notification
        const payload = {
            notification: {
                title: "Quà tặng từ TirTir!",
                body: `Bạn có một voucher ${voucherCode} đặc biệt dành cho giỏ hàng của bạn. Nhanh tay thanh toán nhé!`
            },
            data: {
                action: "OPEN_CART",
                voucherCode: voucherCode || ""
            }
        };
        
        const pushResult = await fcmService.sendToUser(userId, payload, { type: 'VOUCHER_ALERT' });
        
        res.json({ success: true, message: 'Voucher sent successfully', pushResult });
    } catch (error) {
        console.error('sendVoucher Error:', error);
        res.status(500).json({ success: false, message: 'Failed to send voucher' });
    }
};
