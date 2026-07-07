const Review = require('../models/Review');
const Product = require('../models/Product');

/**
 * @desc    Get reviews for a product
 * @route   GET /api/v1/products/:id/reviews
 * @access  Public
 */
exports.getProductReviews = async (req, res) => {
    try {
        const productId = req.params.id;
        let page = parseInt(req.query.page, 10) || 1;
        let limit = parseInt(req.query.limit, 10) || 3;
        const startIndex = (page - 1) * limit;

        const total = await Review.countDocuments({ productId });

        const reviews = await Review.find({ productId })
            .populate({
                path: 'user',
                select: 'firstName lastName avatar'
            })
            .sort('-createdAt')
            .skip(startIndex)
            .limit(limit);

        // Format for frontend
        const formattedReviews = reviews.map(r => ({
            id: r._id,
            rating: r.rating,
            title: r.title,
            comment: r.comment,
            reviewerName: r.user ? `${r.user.firstName || ''} ${r.user.lastName || ''}`.trim() : 'Anonymous',
            reviewerAvatar: r.user ? r.user.avatar : null,
            createdAt: r.createdAt
        }));

        res.status(200).json({
            success: true,
            count: formattedReviews.length,
            total,
            page,
            limit,
            data: formattedReviews
        });
    } catch (err) {
        console.error('Error fetching reviews:', err);
        res.status(500).json({
            success: false,
            message: 'Server Error'
        });
    }
};
