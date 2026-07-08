const Order = require('../models/Order');
const User = require('../models/User');
const Product = require('../models/Product');
const DailyStats = require('../backend/models/daily.stats.model');

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

        // Calculate previous period for trends
        const duration = to.getTime() - from.getTime();
        const prevFrom = new Date(from.getTime() - duration);
        const prevTo = new Date(from.getTime());

        const orderMatch = {
            createdAt: { $gte: from, $lte: to },
            status: { $ne: 'Cancelled' }
        };
        const prevOrderMatch = {
            createdAt: { $gte: prevFrom, $lt: prevTo },
            status: { $ne: 'Cancelled' }
        };

        // Revenue and Orders (Current)
        const revenueAgg = await Order.aggregate([
            { $match: orderMatch },
            { $group: { _id: null, totalRevenue: { $sum: '$totalPrice' }, totalOrders: { $sum: 1 } } }
        ]);
        const totalRevenue = revenueAgg[0]?.totalRevenue || 0;
        const totalOrders = revenueAgg[0]?.totalOrders || 0;
        const averageOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0;

        // Revenue and Orders (Previous)
        const prevRevenueAgg = await Order.aggregate([
            { $match: prevOrderMatch },
            { $group: { _id: null, totalRevenue: { $sum: '$totalPrice' }, totalOrders: { $sum: 1 } } }
        ]);
        const prevTotalRevenue = prevRevenueAgg[0]?.totalRevenue || 0;
        const prevTotalOrders = prevRevenueAgg[0]?.totalOrders || 0;

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

        // Customers (Current)
        const newCustomers = await User.countDocuments({
            role: 'user',
            createdAt: { $gte: from, $lte: to }
        });

        // Active Users (Unique Buyers) in current period
        const uniqueBuyersAgg = await Order.aggregate([
            { $match: orderMatch },
            { $group: { _id: '$userId' } },
            { $count: 'uniqueBuyers' }
        ]);
        const uniqueBuyers = uniqueBuyersAgg[0]?.uniqueBuyers || 0;

        // Total Users for Conversion Rate
        const totalUsers = await User.countDocuments({ role: 'user' });

        // App Sessions (Views) from DailyStats
        const fromDateStr = from.toISOString().split('T')[0];
        const toDateStr = to.toISOString().split('T')[0];
        const prevFromStr = prevFrom.toISOString().split('T')[0];
        const prevToStr = prevTo.toISOString().split('T')[0];

        const viewsAgg = await DailyStats.aggregate([
            { $match: { date: { $gte: fromDateStr, $lte: toDateStr } } },
            { $group: { _id: null, totalViews: { $sum: '$views' } } }
        ]);
        let totalViews = viewsAgg[0]?.totalViews || 0;
        // Fallback for demo if no views tracked yet
        if (totalViews === 0) {
            totalViews = totalOrders * 2;
        }

        const prevViewsAgg = await DailyStats.aggregate([
            { $match: { date: { $gte: prevFromStr, $lt: prevToStr } } },
            { $group: { _id: null, totalViews: { $sum: '$views' } } }
        ]);
        let prevTotalViews = prevViewsAgg[0]?.totalViews || 0;
        if (prevTotalViews === 0) {
            prevTotalViews = prevTotalOrders * 2;
        }

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

        // Calculate Real Trends
        const calcTrend = (current, previous) => {
            if (previous === 0) return current > 0 ? 100 : 0;
            return parseFloat((((current - previous) / previous) * 100).toFixed(1));
        };

        const conversionRate = totalViews > 0 ? parseFloat(((totalOrders / totalViews) * 100).toFixed(1)) : 0;
        const prevConversionRate = prevTotalViews > 0 ? parseFloat(((prevTotalOrders / prevTotalViews) * 100).toFixed(1)) : 0;

        const targetProgress = totalRevenue > 0 ? Math.min(100, Math.round((totalRevenue / 50000000) * 100)) : 0; // Target: 50 million

        const trends = {
            visitorsTrend: calcTrend(totalViews, prevTotalViews),
            ordersTrend: calcTrend(totalOrders, prevTotalOrders),
            viewsTrend: calcTrend(totalViews, prevTotalViews), // Note: Frontend binds tvViews to tvViewsTrend
            conversionTrend: parseFloat((conversionRate - prevConversionRate).toFixed(1)) // absolute diff for %
        };

        const criticalAlerts = [];

        // Real Alert: Low Stock
        if (lowStockCount > 0) {
            criticalAlerts.push({
                type: 'warning',
                title: 'Sắp Hết Hàng',
                message: `Có ${lowStockCount} sản phẩm dưới mức tồn kho an toàn (10).`
            });
        }

        // Real Alert: Pending Orders > 24 hours
        const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
        const overduePending = await Order.countDocuments({
            status: 'Pending',
            createdAt: { $lt: oneDayAgo }
        });
        if (overduePending > 0) {
            criticalAlerts.push({
                type: 'error',
                title: 'Đơn Hàng Tồn Đọng',
                message: `Có ${overduePending} đơn hàng Pending quá 24 giờ chưa được xử lý.`
            });
        }

        // Real Alert: Cancelled Orders Today
        const startOfToday = new Date();
        startOfToday.setHours(0, 0, 0, 0);
        const cancelledToday = await Order.countDocuments({
            status: 'Cancelled',
            updatedAt: { $gte: startOfToday } // Assuming updatedAt is modified on cancel
        });
        if (cancelledToday > 0) {
            criticalAlerts.push({
                type: 'error',
                title: 'Đơn Hàng Bị Hủy',
                message: `Có ${cancelledToday} đơn hàng bị hủy trong ngày hôm nay.`
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
                websiteViews: totalViews,
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
            .populate('userId', 'firstName lastName email')
            .sort({ createdAt: -1 })
            .limit(limit);
        res.json({ success: true, data: orders });
    } catch (error) {
        res.status(500).json({ success: false, message: 'Lỗi khi lấy danh sách đơn hàng' });
    }
};


exports.getMarketingOverview = async (req, res) => {
    try {
        const Campaign = require('../models/Campaign');
        const MarketingActivity = require('../models/MarketingActivity');
        const Order = require('../models/Order');
        const User = require('../models/User');
        const DailyStats = require('../backend/models/daily.stats.model');

        // 1. Performance Insights
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

        // Lấy danh sách những người ĐÃ mua hàng trong vòng 30 ngày qua
        const recentBuyersAgg = await Order.aggregate([
            { $match: { createdAt: { $gte: thirtyDaysAgo } } },
            { $group: { _id: '$userId' } }
        ]);
        const recentBuyerIds = recentBuyersAgg.map(b => b._id);

        // At Risk: Những người có role 'user', đã tạo account lâu hơn 30 ngày, 
        // VÀ KHÔNG NẰM TRONG danh sách những người đã mua hàng 30 ngày qua
        const atRiskUsersCount = await User.countDocuments({
            role: 'user',
            createdAt: { $lt: thirtyDaysAgo },
            _id: { $nin: recentBuyerIds }
        });

        // Vouchers Used & Revenue Recovered
        const orders = await Order.find({ status: 'Delivered' });
        let vouchersUsed = 0;
        let revenueRecovered = 0;

        for (const order of orders) {
            if (order.discount && order.discount > 0) {
                vouchersUsed++;
                revenueRecovered += order.totalPrice;
            }
        }

        // Conversion Rate: Delivered Orders / Total Sessions
        const thirtyDaysAgoStr = thirtyDaysAgo.toISOString().split('T')[0];
        const viewsAgg = await DailyStats.aggregate([
            { $match: { date: { $gte: thirtyDaysAgoStr } } },
            { $group: { _id: null, totalViews: { $sum: '$views' } } }
        ]);
        const totalViews = viewsAgg[0]?.totalViews || (orders.length * 2);
        const conversionRate = totalViews > 0 ? (orders.length / totalViews) * 100 : 0;

        // 2. Active Campaigns
        let campaigns = await Campaign.find({ status: { $in: ['LIVE', 'SCHEDULED'] } }).sort({ endDate: 1 });

        // 3. Marketing Activity
        let activities = await MarketingActivity.find().sort({ createdAt: -1 }).limit(10);

        res.json({
            success: true,
            data: {
                insights: {
                    revenueRecovered: revenueRecovered,
                    atRiskUsers: atRiskUsersCount,
                    vouchersUsed: vouchersUsed,
                    conversionRate: parseFloat(conversionRate.toFixed(2))
                },
                campaigns,
                activities
            }
        });
    } catch (error) {
        console.error('Marketing Overview Error:', error);
        res.status(500).json({ success: false, message: 'Failed to load marketing overview' });
    }
};

exports.createCampaign = async (req, res) => {
    try {
        const { title, message, path, targetAudience, startDate, endDate } = req.body;
        const Campaign = require('../models/Campaign');

        const newCampaign = new Campaign({
            title,
            message,
            path,
            targetAudience: targetAudience || 'All users',
            startDate: startDate || new Date(),
            endDate: endDate || new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // Default 1 week
            status: 'SCHEDULED'
        });

        if (new Date(newCampaign.startDate) <= new Date()) {
            newCampaign.status = 'LIVE';
        }

        await newCampaign.save();

        const MarketingActivity = require('../models/MarketingActivity');
        await MarketingActivity.create({
            type: 'success', // Fixed enum type! It was NOTIFICATION_SENT before which is invalid!
            title: 'Flash Sale Notification Sent',
            targetOrStatus: `Target: ${targetAudience}`
        });

        res.status(201).json({ success: true, message: 'Flash sale broadcasted successfully', data: newCampaign });
    } catch (error) {
        console.error('Create Campaign Error:', error);
        res.status(500).json({ success: false, message: 'Failed to create campaign' });
    }
};

exports.seedMarketingData = async (req, res) => {
    try {
        const Campaign = require('../models/Campaign');
        const MarketingActivity = require('../models/MarketingActivity');

        await Campaign.deleteMany({});
        await MarketingActivity.deleteMany({});

        await Campaign.insertMany([
            {
                title: 'Lunar New Year Flash Sale',
                startDate: new Date(Date.now() - 24 * 60 * 60 * 1000), // Started yesterday
                endDate: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000), // Ends in 5 days
                status: 'LIVE',
                targetRevenue: 50000000,
                currentRevenue: 12500000, // 25% progress
                message: '50% OFF All Skincare!',
                path: '/products',
                targetAudience: 'All users'
            },
            {
                title: 'Spring Radiance Launch',
                startDate: new Date(Date.now() + 10 * 24 * 60 * 60 * 1000), // Starts in 10 days
                endDate: new Date(Date.now() + 20 * 24 * 60 * 60 * 1000),
                status: 'SCHEDULED',
                targetRevenue: 200000000,
                currentRevenue: 0,
                message: 'New spring collection launch.',
                path: '/products/spring',
                targetAudience: 'Premium Segment'
            }
        ]);

        await MarketingActivity.insertMany([
            { type: 'success', title: 'Flash Sale Notification Sent', targetOrStatus: 'Target: 12.4k recipients' },
            { type: 'system', title: 'Voucher "LUNAR24" Created', targetOrStatus: 'Status: Active' },
            { type: 'draft', title: 'Abandoned Cart Email Drafted', targetOrStatus: 'Target: At-risk users' }
        ]);

        res.json({ success: true, message: 'Marketing data seeded successfully' });
    } catch (error) {
        console.error('Seed Error:', error);
        res.status(500).json({ success: false, message: 'Failed to seed data' });
    }
};

exports.getAdminVoucherStats = async (req, res) => {
    try {
        const Coupon = require('../backend/models/coupon.model');
        const coupons = await Coupon.find();

        let total = coupons.length;
        let active = 0;
        let totalUsage = 0;
        let totalDiscountValue = 0;
        let percentCount = 0;

        coupons.forEach(c => {
            const now = new Date();
            if (c.active && c.validFrom <= now && c.validTo >= now && c.usedCount < c.usageLimit) {
                active++;
            }
            totalUsage += c.usedCount;
            if (c.discountType === 'percentage') {
                percentCount++;
                totalDiscountValue += c.discountValue;
            } else if (c.discountType === 'fixed') {
                totalDiscountValue += (c.discountValue / 1000); // Approximate normalization for stats
            }
        });

        let avgDiscountValue = percentCount > 0 ? (totalDiscountValue / percentCount) : 0;

        res.json({
            success: true,
            data: {
                total,
                active,
                totalUsage,
                totalDiscountValue,
                avgDiscountValue
            }
        });
    } catch (error) {
        console.error('getAdminVoucherStats Error:', error);
        res.status(500).json({ success: false, message: 'Failed to fetch voucher stats' });
    }
};

exports.getAdminVouchers = async (req, res) => {
    try {
        const Coupon = require('../backend/models/coupon.model');
        const coupons = await Coupon.find().sort({ createdAt: -1 });
        res.json({ success: true, data: coupons });
    } catch (error) {
        console.error('getAdminVouchers Error:', error);
        res.status(500).json({ success: false, message: 'Failed to fetch vouchers' });
    }
};

exports.getAdminVoucherById = async (req, res) => {
    try {
        const Coupon = require('../backend/models/coupon.model');
        const coupon = await Coupon.findById(req.params.id);
        if (!coupon) return res.status(404).json({ success: false, message: 'Voucher not found' });
        res.json({ success: true, data: coupon });
    } catch (error) {
        console.error('getAdminVoucherById Error:', error);
        res.status(500).json({ success: false, message: 'Failed to fetch voucher' });
    }
};

exports.createAdminVoucher = async (req, res) => {
    try {
        const Coupon = require('../backend/models/coupon.model');
        const newCoupon = new Coupon(req.body);
        await newCoupon.save();
        res.status(201).json({ success: true, data: newCoupon, message: 'Voucher created successfully' });
    } catch (error) {
        console.error('createAdminVoucher Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Failed to create voucher' });
    }
};

exports.updateAdminVoucher = async (req, res) => {
    try {
        const Coupon = require('../backend/models/coupon.model');
        const updatedCoupon = await Coupon.findByIdAndUpdate(req.params.id, req.body, { new: true, runValidators: true });
        if (!updatedCoupon) return res.status(404).json({ success: false, message: 'Voucher not found' });
        res.json({ success: true, data: updatedCoupon, message: 'Voucher updated successfully' });
    } catch (error) {
        console.error('updateAdminVoucher Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Failed to update voucher' });
    }
};

exports.deleteAdminVoucher = async (req, res) => {
    try {
        const Coupon = require('../backend/models/coupon.model');
        const deletedCoupon = await Coupon.findByIdAndDelete(req.params.id);
        if (!deletedCoupon) return res.status(404).json({ success: false, message: 'Voucher not found' });
        res.json({ success: true, message: 'Voucher deleted successfully' });
    } catch (error) {
        console.error('deleteAdminVoucher Error:', error);
        res.status(500).json({ success: false, message: 'Failed to delete voucher' });
    }
};

exports.getRetentionAnalytics = async (req, res) => {
    try {
        const User = require('../models/User');
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

        const activeUsers = await User.countDocuments({ lastActiveDate: { $gte: thirtyDaysAgo } });
        const inactiveUsers = await User.countDocuments({ lastActiveDate: { $lt: thirtyDaysAgo } });
        const totalUsers = activeUsers + inactiveUsers;
        const rate = totalUsers > 0 ? ((activeUsers / totalUsers) * 100).toFixed(1) : 0;

        res.json({
            success: true,
            data: {
                active: activeUsers,
                inactive: inactiveUsers,
                rate: rate + '%'
            }
        });
    } catch (error) {
        console.error('getRetentionAnalytics Error:', error);
        res.status(500).json({ success: false, message: 'Failed to get analytics' });
    }
};

exports.getAtRiskUsers = async (req, res) => {
    try {
        const User = require('../models/User');
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
        const sixtyDaysAgo = new Date();
        sixtyDaysAgo.setDate(sixtyDaysAgo.getDate() - 60);

        // Fetch users who are at risk (inactive for > 30 days but have purchased before)
        const atRiskUsers = await User.find({
            lastActiveDate: { $lt: thirtyDaysAgo },
            totalSpent: { $gt: 0 }
        }).sort({ totalSpent: -1 }).limit(20);

        const formattedUsers = atRiskUsers.map(user => {
            let status = 'High Risk';
            if (user.lastActiveDate < sixtyDaysAgo) {
                status = 'Slipping Away';
            }
            return {
                id: user._id,
                name: `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email,
                avatar: user.avatar,
                ltv: user.totalSpent,
                status: status,
                lastActiveStr: user.lastActiveDate ? user.lastActiveDate.toISOString() : null
            };
        });

        res.json({ success: true, data: formattedUsers });
    } catch (error) {
        console.error('getAtRiskUsers Error:', error);
        res.status(500).json({ success: false, message: 'Failed to fetch users' });
    }
};


