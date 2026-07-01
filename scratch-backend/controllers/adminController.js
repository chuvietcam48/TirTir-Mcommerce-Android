const Order = require('../models/Order');
const User = require('../models/User');
const Product = require('../models/Product');

const parseRange = (query) => {
    const range = query.range || '30d';
    const to = new Date();
    const from = new Date();
    switch (range) {
        case 'today':
            from.setHours(0, 0, 0, 0);
            break;
        case '7d':
            from.setDate(from.getDate() - 7);
            break;
        case '90d':
            from.setDate(from.getDate() - 90);
            break;
        case '30d':
        default:
            from.setDate(from.getDate() - 30);
            break;
    }
    return { from, to };
};

const _getTopProductsAgg = async (orderMatch, limit = 10) => {
    const top = await Order.aggregate([
        { $match: orderMatch },
        { $unwind: '$items' },
        {
            $group: {
                _id: '$items.product',
                name: { $first: '$items.name' },
                image: { $first: '$items.image' },
                totalSold: { $sum: '$items.quantity' },
                totalRevenue: { $sum: { $multiply: ['$items.price', '$items.quantity'] } }
            }
        },
        { $sort: { totalSold: -1 } },
        { $limit: limit }
    ]);

    const populated = await Product.populate(top, {
        path: '_id',
        select: 'Name Product_ID Thumbnail_Images'
    });

    return populated
        .filter((i) => i._id)
        .map((i) => ({
            product: {
                _id: i._id._id.toString(),
                name: i._id.Name || i.name,
                sku: i._id.Product_ID,
                mainImage: Array.isArray(i._id.Thumbnail_Images)
                    ? i._id.Thumbnail_Images[0]
                    : i._id.Thumbnail_Images || i.image
            },
            salesCount: i.totalSold,
            revenue: i.totalRevenue
        }));
};

exports.getOverview = async (req, res) => {
    try {
        const { from, to } = parseRange(req.query);
        const orderMatch = {
            createdAt: { $gte: from, $lte: to },
            status: { $ne: 'Cancelled' }
        };

        // Revenue and Orders
        const revenueAgg = await Order.aggregate([
            { $match: orderMatch },
            { $group: { _id: null, totalRevenue: { $sum: '$totalPrice' }, totalOrders: { $sum: 1 } } }
        ]);
        const totalRevenue = revenueAgg[0]?.totalRevenue || 0;
        const totalOrders = revenueAgg[0]?.totalOrders || 0;
        const averageOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0;

        const deliveredOrders = await Order.countDocuments({
            createdAt: { $gte: from, $lte: to },
            status: 'Delivered'
        });

        // Status breakdown
        const statusAgg = await Order.aggregate([
            { $match: { createdAt: { $gte: from, $lte: to } } },
            { $group: { _id: '$status', count: { $sum: 1 } } }
        ]);
        const orderStatusBreakdown = statusAgg.reduce((acc, cur) => {
            acc[cur._id] = cur.count;
            return acc;
        }, {});
        
        ['Pending', 'Processing', 'Shipped', 'Delivered', 'Cancelled'].forEach((s) => {
            if (orderStatusBreakdown[s] === undefined) orderStatusBreakdown[s] = 0;
        });

        // Customers
        const newCustomers = await User.countDocuments({
            role: 'user',
            createdAt: { $gte: from, $lte: to }
        });

        // Revenue Series
        const revenueSeriesAgg = await Order.aggregate([
            { $match: orderMatch },
            { $group: { _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } }, revenue: { $sum: '$totalPrice' }, orderCount: { $sum: 1 } } },
            { $sort: { _id: 1 } }
        ]);
        const revenueSeries = revenueSeriesAgg.map((p) => ({ date: p._id, revenue: p.revenue, orderCount: p.orderCount }));

        // Top Products
        const topProducts = await _getTopProductsAgg(orderMatch, 10);
        
        // Low Stock
        const lowStockCount = await Product.countDocuments({ Stock_Quantity: { $lt: 10 } });

        // Trends and Target (Mocked for now as per HTML design)
        const targetProgress = 82;
        const conversionRate = 82; // 82%
        const trends = {
            visitorsTrend: 4.8,
            ordersTrend: 2.5,
            viewsTrend: -1.8,
            conversionTrend: 2.0
        };

        const criticalAlerts = [];
        // Add a mock complaint
        criticalAlerts.push({
            type: 'error',
            title: 'New Complaint',
            message: 'Order #TR-9928 has a quality dispute.'
        });
        if (lowStockCount > 0) {
            criticalAlerts.push({
                type: 'warning',
                title: 'Low Stock Alert',
                message: `${lowStockCount} products are below 10 units.`
            });
        }

        res.json({
            range: { from, to },
            summary: {
                totalRevenue,
                totalOrders,
                deliveredOrders,
                newCustomers,
                averageOrderValue,
                lowStockCount,
                websiteViews: Math.floor(Math.random() * 500) + 100, // Mock since we don't have DailyStats
                conversionRate
            },
            trends,
            targetProgress,
            criticalAlerts,
            orderStatusBreakdown,
            revenueSeries,
            topProducts
        });
    } catch (error) {
        console.error('Admin Overview Error:', error);
        res.status(500).json({ message: 'Failed to load admin overview' });
    }
};

exports.getTopProducts = async (req, res) => {
    try {
        const orderMatch = { status: { $ne: 'Cancelled' } };
        const formatted = await _getTopProductsAgg(orderMatch, 10);
        res.json(formatted);
    } catch (error) {
        res.status(500).json({ message: 'Lỗi khi lấy danh sách sản phẩm bán chạy' });
    }
};

exports.getMetrics = async (req, res) => {
    try {
        const { from } = parseRange(req.query);
        const orderMatch = {
            createdAt: { $gte: from },
            status: { $ne: 'Cancelled' }
        };

        const [series, totals] = await Promise.all([
            Order.aggregate([
                { $match: orderMatch },
                {
                    $group: {
                        _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } },
                        revenue: { $sum: '$totalPrice' },
                        orderCount: { $sum: 1 }
                    }
                },
                { $sort: { _id: 1 } }
            ]),
            Order.aggregate([
                { $match: orderMatch },
                { $group: { _id: null, totalRevenue: { $sum: '$totalPrice' }, totalOrders: { $sum: 1 } } }
            ])
        ]);

        res.json({
            totalRevenue: totals[0]?.totalRevenue || 0,
            totalOrders: totals[0]?.totalOrders || 0,
            series: series.map(p => ({ date: p._id, revenue: p.revenue, orderCount: p.orderCount }))
        });
    } catch (error) {
        res.status(500).json({ message: 'Lỗi khi lấy metrics' });
    }
};

exports.getAdminOrders = async (req, res) => {
    try {
        const limit = parseInt(req.query.limit) || 10;
        const orders = await Order.find({})
            .populate('user', 'firstName lastName email')
            .sort({ createdAt: -1 })
            .limit(limit);
        res.json(orders);
    } catch (error) {
        res.status(500).json({ message: 'Lỗi khi lấy danh sách đơn hàng' });
    }
};
