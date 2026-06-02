const Order = require('../models/order.model');
const User = require('../models/user.model');
const Product = require('../models/product.model');
const DailyStats = require('../models/daily.stats.model');
const Cart = require('../models/cart.model');
const { ORDER_STATUS } = require('../constants');

// ─── Helpers ────────────────────────────────────────────────────────────────
const formatYmd = (d) => new Date(d).toISOString().slice(0, 10);

/**
 * Shared top-products aggregation used by both getOverview and getTopProducts.
 * Product.Name is the correct field (not Product_Name — verified in product model).
 * @param {object} orderMatch - MongoDB match stage filter
 * @param {number} limit - max results
 */
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
        select: 'Name Product_ID Thumbnail_Images'  // 'Name' not 'Product_Name'
    });

    return populated
        .filter((i) => i._id)
        .map((i) => ({
            product: {
                _id: i._id._id.toString(),
                name: i._id.Name || i.name,  // Product.Name is the correct field
                sku: i._id.Product_ID,
                mainImage: Array.isArray(i._id.Thumbnail_Images)
                    ? i._id.Thumbnail_Images[0]
                    : i._id.Thumbnail_Images || i.image
            },
            salesCount: i.totalSold,
            revenue: i.totalRevenue
        }));
};

function parseRange({ range = '30d', from, to }) {
    const now = new Date();

    if (range === 'custom') {
        const fromDate = from ? new Date(from) : new Date(now);
        const toDate = to ? new Date(to) : new Date(now);
        // Normalize end-of-day
        toDate.setHours(23, 59, 59, 999);
        return { range, from: fromDate, to: toDate };
    }

    const start = new Date(now);
    switch (range) {
        case 'today':
            start.setHours(0, 0, 0, 0);
            break;
        case '7d':
            start.setDate(start.getDate() - 7);
            break;
        case '30d':
            start.setDate(start.getDate() - 30);
            break;
        case '90d':
            start.setDate(start.getDate() - 90);
            break;
        default:
            start.setDate(start.getDate() - 30);
            range = '30d';
            break;
    }
    return { range, from: start, to: now };
}

// GET /api/admin/dashboard/stats
exports.getStats = async (req, res) => {
    try {
        // 1. Total Orders
        const totalOrders = await Order.countDocuments();

        // 2. Total Revenue (excluding Cancelled)
        const revenueResult = await Order.aggregate([
            { $match: { status: { $ne: ORDER_STATUS.CANCELLED } } },
            { $group: { _id: null, total: { $sum: '$totalAmount' } } }
        ]);
        const totalRevenue = revenueResult.length > 0 ? revenueResult[0].total : 0;

        // 3. Total Customers
        const totalCustomers = await User.countDocuments({ role: 'user' });

        // 4. Low Stock Products (stock < 10)
        const lowStockProducts = await Product.countDocuments({
            Stock_Quantity: { $lt: 10 }
        });

        // Return in format expected by frontend
        res.json({
            totalOrders,
            totalRevenue,
            totalCustomers,
            lowStockProducts
        });

    } catch (error) {
        console.error("Dashboard Stats Error:", error);
        res.status(500).json({ message: "Lỗi khi lấy thống kê dashboard" });
    }
};

/**
 * GET /api/v1/admin/dashboard/overview
 * Query:
 *   range=today|7d|30d|90d|custom
 *   from=YYYY-MM-DD (custom)
 *   to=YYYY-MM-DD   (custom)
 *
 * Returns a single, consistent payload for the Admin "General" overview tab.
 */
exports.getOverview = async (req, res) => {
    try {
        const { range, from, to } = parseRange(req.query || {});

        // Core: Orders + Revenue (range-scoped, excluding cancelled)
        const orderMatch = {
            createdAt: { $gte: from, $lte: to },
            status: { $ne: ORDER_STATUS.CANCELLED }
        };

        const revenueAgg = await Order.aggregate([
            { $match: orderMatch },
            { $group: { _id: null, totalRevenue: { $sum: '$totalAmount' }, totalOrders: { $sum: 1 } } }
        ]);
        const totalRevenue = revenueAgg[0]?.totalRevenue || 0;
        const totalOrders = revenueAgg[0]?.totalOrders || 0;
        const averageOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0;

        const deliveredOrders = await Order.countDocuments({
            createdAt: { $gte: from, $lte: to },
            status: ORDER_STATUS.DELIVERED
        });

        const statusAgg = await Order.aggregate([
            { $match: { createdAt: { $gte: from, $lte: to } } },
            { $group: { _id: '$status', count: { $sum: 1 } } }
        ]);
        const orderStatusBreakdown = statusAgg.reduce((acc, cur) => {
            acc[cur._id] = cur.count;
            return acc;
        }, {});
        Object.values(ORDER_STATUS).forEach((s) => {
            if (orderStatusBreakdown[s] === undefined) orderStatusBreakdown[s] = 0;
        });

        const newCustomers = await User.countDocuments({
            role: 'user',
            createdAt: { $gte: from, $lte: to }
        });

        const revenueSeriesAgg = await Order.aggregate([
            { $match: orderMatch },
            { $group: { _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } }, revenue: { $sum: '$totalAmount' }, orderCount: { $sum: 1 } } },
            { $sort: { _id: 1 } }
        ]);
        const revenueSeries = revenueSeriesAgg.map((p) => ({ date: p._id, revenue: p.revenue, orderCount: p.orderCount }));

        // Defaults for optional blocks
        let recentOrders = [];
        let lowStockAlerts = [];
        let lowStockCount = 0;
        let trafficAgg = [];
        let websiteViews = 0;
        let customerGrowthSeries = [];
        let topProducts = [];
        let cartRecovery = null;

        // Optional: Recent orders
        try {
            recentOrders = await Order.find({})
                .populate('user', 'name email')
                .sort({ createdAt: -1 })
                .limit(5)
                .select('user totalAmount status createdAt');
        } catch (e) {
            console.warn('[Overview] recentOrders failed:', e.message);
        }

        // Optional: Low stock alerts
        try {
            const threshold = parseInt(req.query.threshold || '10', 10);
            lowStockAlerts = await Product.find({ Stock_Quantity: { $lt: threshold } })
                .sort({ Stock_Quantity: 1 })
                .limit(10)
                .select('Product_ID Name Stock_Quantity Stock_Reserved Thumbnail_Images Category');
            lowStockCount = await Product.countDocuments({ Stock_Quantity: { $lt: threshold } });
        } catch (e) {
            console.warn('[Overview] lowStock failed:', e.message);
        }

        // Optional: Website views (DailyStats)
        try {
            const fromYmdStr = formatYmd(from);
            const toYmdStr = formatYmd(to);
            trafficAgg = await DailyStats.aggregate([
                { $match: { date: { $gte: fromYmdStr, $lte: toYmdStr } } },
                { $project: { _id: 0, date: 1, views: 1 } },
                { $sort: { date: 1 } }
            ]);
            websiteViews = trafficAgg.reduce((sum, p) => sum + (p.views || 0), 0);
        } catch (e) {
            console.warn('[Overview] traffic failed:', e.message);
        }

        // Optional: Customer growth trend (last 6 months)
        try {
            const sixMonthsAgo = new Date();
            sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6);
            const growthAgg = await User.aggregate([
                { $match: { role: 'user', createdAt: { $gte: sixMonthsAgo } } },
                { $group: { _id: { $dateToString: { format: '%Y-%m', date: '$createdAt' } }, count: { $sum: 1 } } },
                { $sort: { _id: 1 } }
            ]);
            customerGrowthSeries = growthAgg.map((p) => ({ month: p._id, count: p.count }));
        } catch (e) {
            console.warn('[Overview] customerGrowth failed:', e.message);
        }

        // Optional: Top products — reuse shared helper to avoid duplicate logic
        try {
            topProducts = await _getTopProductsAgg(orderMatch, 10);
        } catch (e) {
            console.warn('[Overview] topProducts failed:', e.message);
        }

        // Optional: Cart recovery metrics (only if reliable)
        try {
            const totalAbandoned = await Cart.countDocuments({ recoveryStatus: { $nin: ['recovered', 'abandoned_final'] } });
            const recoveryStats = await Order.aggregate([
                { $match: { status: { $ne: ORDER_STATUS.CANCELLED }, recoveredFrom: { $in: ['email_1', 'email_2', 'email_3', 'manual'] } } },
                { $group: { _id: null, recoveredCount: { $sum: 1 }, recoveredRevenue: { $sum: '$totalAmount' } } }
            ]);
            const recoveredCount = recoveryStats[0]?.recoveredCount || 0;
            const recoveredRevenue = recoveryStats[0]?.recoveredRevenue || 0;
            const totalSentOrPending = totalAbandoned + recoveredCount;
            const conversionRate = totalSentOrPending > 0 ? Number(((recoveredCount / totalSentOrPending) * 100).toFixed(2)) : 0;
            cartRecovery = { totalAbandoned, recoveredCount, recoveredRevenue, conversionRate };
        } catch (e) {
            cartRecovery = null;
        }

        const response = {
            range: { range, from: formatYmd(from), to: formatYmd(to) },
            summary: {
                totalRevenue,
                totalOrders,
                deliveredOrders,
                newCustomers,
                averageOrderValue,
                lowStockCount,
                websiteViews
            },
            orderStatusBreakdown,
            revenueSeries,
            traffic: {
                viewsTotal: websiteViews,
                viewsSeries: trafficAgg
            },
            customerGrowthSeries,
            topProducts,
            recentOrders,
            lowStockAlerts
        };

        if (cartRecovery) response.cartRecovery = cartRecovery;

        res.json(response);
    } catch (error) {
        console.error('Admin Overview Error:', error);
        res.status(500).json({ message: 'Failed to load admin overview' });
    }
};

// GET /api/admin/dashboard/revenue
exports.getRevenueChart = async (req, res) => {
    try {
        const { startDate, endDate } = req.query;

        let query = { status: { $ne: ORDER_STATUS.CANCELLED } };

        if (startDate && endDate) {
            query.createdAt = {
                $gte: new Date(startDate),
                $lte: new Date(endDate)
            };
        } else {
            // Default to last 30 days
            const thirtyDaysAgo = new Date();
            thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
            query.createdAt = { $gte: thirtyDaysAgo };
        }

        const revenueData = await Order.aggregate([
            { $match: query },
            {
                $group: {
                    _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } },
                    revenue: { $sum: '$totalAmount' },
                    count: { $sum: 1 }
                }
            },
            { $sort: { _id: 1 } }
        ]);

        // Transform to format expected by frontend: {labels: [], data: []}
        const labels = revenueData.map(item => {
            const date = new Date(item._id);
            return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
        });
        const data = revenueData.map(item => item.revenue);

        res.json({ labels, data });
    } catch (error) {
        console.error("Revenue Chart Error:", error);
        res.status(500).json({ message: "Lỗi khi lấy biểu đồ doanh thu" });
    }
};

// GET /api/admin/dashboard/top-products
exports.getTopProducts = async (req, res) => {
    try {
        const limit = Math.min(parseInt(req.query.limit) || 10, 50);
        const orderMatch = { status: { $ne: ORDER_STATUS.CANCELLED } };
        const formatted = await _getTopProductsAgg(orderMatch, limit);
        res.json(formatted);
    } catch (error) {
        console.error("Top Products Error:", error);
        res.status(500).json({ message: "Lỗi khi lấy danh sách sản phẩm bán chạy" });
    }
};

// GET /api/admin/dashboard/customers
exports.getCustomerStats = async (req, res) => {
    try {
        // 1. Total Customers
        const totalCustomers = await User.countDocuments({ role: 'user' });

        // 2. New Customers (Last 30 days)
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
        const newCustomersLast30Days = await User.countDocuments({
            createdAt: { $gte: thirtyDaysAgo },
            role: 'user'
        });

        // 3. Customer Growth Chart (Last 6 months maybe?) - Optional, but user asked for "Customer analytics"
        // Let's return new customers by month for the last 6 months
        const sixMonthsAgo = new Date();
        sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6);

        const customerGrowth = await User.aggregate([
            {
                $match: {
                    role: 'user',
                    createdAt: { $gte: sixMonthsAgo }
                }
            },
            {
                $group: {
                    _id: { $dateToString: { format: '%Y-%m', date: '$createdAt' } },
                    count: { $sum: 1 }
                }
            },
            { $sort: { _id: 1 } }
        ]);

        res.json({
            totalCustomers,
            newCustomersLast30Days,
            customerGrowth
        });
    } catch (error) {
        console.error("Customer Stats Error:", error);
        res.status(500).json({ message: "Lỗi khi lấy thống kê khách hàng" });
    }
};

// GET /api/admin/orders/stats
exports.getOrderStats = async (req, res) => {
    try {
        const stats = await Order.aggregate([
            {
                $group: {
                    _id: '$status',
                    count: { $sum: 1 }
                }
            }
        ]);

        // Transform to object: { Pending: 5, Processing: 2, ... }
        const formattedStats = stats.reduce((acc, curr) => {
            acc[curr._id] = curr.count;
            return acc;
        }, {});

        // Ensure all statuses are present (even if 0)
        const allStatuses = Object.values(ORDER_STATUS);
        allStatuses.forEach(status => {
            if (!formattedStats[status]) {
                formattedStats[status] = 0;
            }
        });

        res.json(formattedStats);
    } catch (error) {
        console.error("Order Stats Error:", error);
        res.status(500).json({ message: "Lỗi khi lấy thống kê đơn hàng" });
    }
};

// GET /api/admin/orders
exports.getAllOrders = async (req, res) => {
    try {
        const page = parseInt(req.query.page);
        const limit = parseInt(req.query.limit) || 10;
        const skip = page ? (page - 1) * limit : 0;

        const statusFilter = req.query.status;
        const search = req.query.search;
        const startDate = req.query.startDate; // ISO date string: 2024-01-01
        const endDate = req.query.endDate;     // ISO date string: 2024-12-31

        let query = {};

        if (statusFilter) {
            query.status = statusFilter;
        }

        if (search) {
            const isValidObjectId = (id) => /^[0-9a-fA-F]{24}$/.test(id);
            if (isValidObjectId(search)) {
                query._id = search;
            }
        }

        // Date range filter
        if (startDate || endDate) {
            query.createdAt = {};
            if (startDate) query.createdAt.$gte = new Date(startDate);
            if (endDate) {
                // Include the full endDate day (set to 23:59:59)
                const end = new Date(endDate);
                end.setHours(23, 59, 59, 999);
                query.createdAt.$lte = end;
            }
        }

        // If no page specified (dashboard widget), return simple array of recent orders
        if (!page) {
            const orders = await Order.find(query)
                .populate('user', 'name email')
                .sort({ createdAt: -1 })
                .limit(limit);

            return res.json(orders);
        }

        // If page specified (order management page), return paginated response
        const totalOrders = await Order.countDocuments(query);
        const orders = await Order.find(query)
            .populate('user', 'name email')
            .sort({ createdAt: -1 })
            .skip(skip)
            .limit(limit);

        res.json({
            orders,
            page,
            pages: Math.ceil(totalOrders / limit),
            total: totalOrders
        });
    } catch (error) {
        console.error("Get All Orders Error:", error);
        res.status(500).json({ message: "Lỗi khi lấy danh sách đơn hàng" });
    }
};
