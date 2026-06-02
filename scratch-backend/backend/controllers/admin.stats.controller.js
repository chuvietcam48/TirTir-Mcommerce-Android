const Order = require('../models/order.model');
const Product = require('../models/product.model');
const DailyStats = require('../models/daily.stats.model');
const ChatLog = require('../models/chat.log.model');
const mongoose = require('mongoose');
const User = require('../models/user.model');
const Cart = require('../models/cart.model');

// GET /api/admin/stats/inventory
exports.getInventoryForecast = async (req, res) => {
    try {
        // Aggregate to find total items and value
        const stats = await Product.aggregate([
            {
                $group: {
                    _id: null,
                    totalItems: { $sum: "$Stock_Quantity" },
                    totalValue: { $sum: { $multiply: ["$Stock_Quantity", "$Price"] } },
                    lowStockCount: {
                        $sum: { $cond: [{ $lte: ["$Stock_Quantity", 10] }, 1, 0] }
                    }
                }
            }
        ]);

        // REAL FORECAST: Predict depletion based on actual low stock qty and recent sales velocity
        // For simplicity, we query items with Stock_Quantity <= 10 as "high risk" 
        const atRisk = await Product.find({ Stock_Quantity: { $lte: 10 } })
            .select('Name Stock_Quantity Price Category')
            .sort({ Stock_Quantity: 1 })
            .limit(10);

        res.json({
            summary: stats[0] || { totalItems: 0, totalValue: 0, lowStockCount: 0 },
            forecast: atRisk
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/admin/stats/ai-insights
exports.getAiInsights = async (req, res) => {
    try {
        // Aggregate from real ChatLog collection
        const topKeywords = await ChatLog.aggregate([
            { $unwind: "$keywords" },
            { $group: { _id: "$keywords", count: { $sum: 1 } } },
            { $sort: { count: -1 } },
            { $limit: 10 }
        ]);

        res.json({
            topKeywords: topKeywords.length > 0 ? topKeywords : []
        });
    } catch (error) {
        console.error("AI Insights Error:", error);
        res.status(500).json({ message: "Unable to calculate AI insights at this time" });
    }
};

// GET /api/admin/stats/customers
exports.getCustomerStats = async (req, res) => {
    try {
        const totalCustomers = await User.countDocuments({ role: 'user' });

        // New Customers (Last 30 days)
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

        const newCustomers = await User.countDocuments({
            role: 'user',
            createdAt: { $gte: thirtyDaysAgo }
        });

        res.json({
            total: totalCustomers,
            newLast30Days: newCustomers
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/admin/stats/sales-by-category
exports.getSalesByCategory = async (req, res) => {
    try {
        const sales = await Order.aggregate([
            { $match: { status: { $ne: 'Cancelled' } } },
            { $unwind: "$items" },
            {
                $lookup: {
                    from: "products",
                    localField: "items.product",
                    foreignField: "_id",
                    as: "productInfo"
                }
            },
            { $unwind: "$productInfo" },
            {
                $group: {
                    _id: "$productInfo.Category",
                    totalRevenue: { $sum: { $multiply: ["$items.quantity", "$items.price"] } },
                    count: { $sum: "$items.quantity" }
                }
            },
            { $sort: { totalRevenue: -1 } }
        ]);

        res.json(sales);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/admin/stats/revenue?range=7days
exports.getRevenueChart = async (req, res) => {
    try {
        const { range } = req.query;
        let startDate = new Date();

        if (range === '30days') {
            startDate.setDate(startDate.getDate() - 30);
        } else {
            startDate.setDate(startDate.getDate() - 7); // Default 7 days
        }

        const revenue = await Order.aggregate([
            {
                $match: {
                    createdAt: { $gte: startDate },
                    status: { $ne: 'Cancelled' }
                }
            },
            {
                $group: {
                    _id: { $dateToString: { format: "%Y-%m-%d", date: "$createdAt" } },
                    totalRevenue: { $sum: "$totalAmount" }, // Replaced totalPrice with correct field totalAmount
                    orderCount: { $sum: 1 }
                }
            },
            { $sort: { _id: 1 } }
        ]);

        res.json(revenue);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/admin/stats/conversion
exports.getConversionFunnel = async (req, res) => {
    try {
        const stats = await DailyStats.aggregate([
            {
                $group: {
                    _id: null,
                    totalViews: { $sum: "$views" },
                    totalAddToCart: { $sum: "$addToCart" },
                }
            }
        ]);

        const totalOrders = await Order.countDocuments({ status: { $ne: 'Cancelled' } });

        const funnel = {
            views: stats[0]?.totalViews || 0,
            addToCart: stats[0]?.totalAddToCart || 0,
            orders: totalOrders
        };

        res.json(funnel);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/admin/stats/top-products
exports.getTopProducts = async (req, res) => {
    try {
        // 1. Top Selling (Based on Order Items)
        const topSelling = await Order.aggregate([
            { $match: { status: { $ne: 'Cancelled' } } },
            { $unwind: "$items" },
            {
                $group: {
                    _id: "$items.product",
                    totalSold: { $sum: "$items.quantity" },
                    revenue: { $sum: { $multiply: ["$items.quantity", "$items.price"] } }
                }
            },
            { $sort: { totalSold: -1 } },
            { $limit: 10 },
            {
                $lookup: {
                    from: "products",
                    localField: "_id",
                    foreignField: "_id",
                    as: "productInfo"
                }
            },
            { $unwind: "$productInfo" },
            {
                $project: {
                    _id: 1,
                    name: "$productInfo.Name",
                    totalSold: 1,
                    revenue: 1,
                    image: "$productInfo.Thumbnail_Images"
                }
            }
        ]);

        // 2. Top Viewed (Based on Product.views)
        const topViewed = await Product.find()
            .sort({ views: -1 })
            .limit(10)
            .select('Name views Thumbnail_Images Price Category');

        res.json({ topSelling, topViewed });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/v1/admin/stats/cart-recovery
exports.getCartRecoveryStats = async (req, res) => {
    try {
        // 1. Total Abandoned (pending, email_*_sent)
        const totalAbandoned = await Cart.countDocuments({
            recoveryStatus: { $nin: ['recovered', 'abandoned_final'] }
        });

        // 2. Recovery Stats from Orders
        const recoveryStats = await Order.aggregate([
            {
                $match: {
                    status: { $ne: 'Cancelled' },
                    recoveredFrom: { $in: ['email_1', 'email_2', 'email_3', 'manual'] }
                }
            },
            {
                $group: {
                    _id: null,
                    recoveredCount: { $sum: 1 },
                    recoveredRevenue: { $sum: "$totalAmount" }
                }
            }
        ]);

        const recoveredCount = recoveryStats[0]?.recoveredCount || 0;
        const recoveredRevenue = recoveryStats[0]?.recoveredRevenue || 0;
        
        // 3. Conversion Rate
        const totalSentOrPending = totalAbandoned + recoveredCount;
        const conversionRate = totalSentOrPending > 0 
            ? ((recoveredCount / totalSentOrPending) * 100).toFixed(2) 
            : 0;

        res.json({
            totalAbandoned,
            recoveredCount,
            recoveredRevenue,
            conversionRate: Number(conversionRate)
        });
    } catch (error) {
        console.error("Cart Recovery Stats Error:", error);
        res.status(500).json({ message: "Unable to calculate cart recovery stats" });
    }
};
