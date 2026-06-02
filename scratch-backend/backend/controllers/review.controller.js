const Review = require('../models/review.model');
const Order = require('../models/order.model');
const Product = require('../models/product.model');
const { ORDER_STATUS } = require('../constants');
const mongoose = require('mongoose');

// Helper to resolve Product ObjectId from param (ID, Slug, or Product_ID)
const resolveProductId = async (idParam) => {
    if (mongoose.Types.ObjectId.isValid(idParam)) {
        return idParam;
    }
    const product = await Product.findOne({
        $or: [{ slug: idParam }, { Product_ID: idParam }]
    });
    return product ? product._id : null;
};

// @desc    Get reviews for a product
// @route   GET /api/v1/products/:id/reviews
// @access  Public
// ... existing code ...

// GET /api/v1/admin/reviews (Admin Only)
exports.getAllReviewsAdmin = async (req, res) => {
    try {
        const page = parseInt(req.query.page) || 1;
        const limit = parseInt(req.query.limit) || 10;
        const skip = (page - 1) * limit;
        const rating = req.query.rating; // Filter by rating

        let query = {};
        if (rating) query.rating = rating;

        const total = await Review.countDocuments(query);
        const reviews = await Review.find(query)
            .populate('user', 'name email')
            .populate('product', 'Name Thumbnail_Images Product_Slug slug')
            .sort({ createdAt: -1 })
            .skip(skip)
            .limit(limit);

        const normalizedReviews = reviews.map((review) => {
            const plain = review.toObject();
            if (plain.product) {
                plain.product.Product_Slug = plain.product.Product_Slug || plain.product.slug || '';
                // Backward-compatible alias expected by admin UI / integrations.
                plain.product_id = plain.product;
            }
            return plain;
        });

        res.json({
            reviews: normalizedReviews,
            page,
            pages: Math.ceil(total / limit),
            total
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/v1/reviews/:id (Admin Only)
exports.getReviewByIdAdmin = async (req, res) => {
    try {
        const review = await Review.findById(req.params.id)
            .populate('user', 'name email')
            .populate('product', 'Name Thumbnail_Images Product_Slug slug');

        if (!review) {
            return res.status(404).json({ message: 'Review not found' });
        }

        const normalized = review.toObject();
        if (normalized.product) {
            normalized.product.Product_Slug = normalized.product.Product_Slug || normalized.product.slug || '';
            normalized.product_id = normalized.product;
        }

        res.json({
            success: true,
            data: normalized
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

exports.getProductReviews = async (req, res) => {
    // ... existing code ...
    try {
        const rawId = req.params.id;

        // Support admin list endpoint at GET /api/v1/reviews
        if (!rawId) {
            const page = parseInt(req.query.page, 10) || 1;
            const limit = parseInt(req.query.limit, 10) || 20;
            const skip = (page - 1) * limit;
            const rating = req.query.rating;
            const query = rating ? { rating: Number(rating) } : {};

            const total = await Review.countDocuments(query);
            const reviews = await Review.find(query)
                .populate('user', 'name email')
                .populate('product', 'Name Thumbnail_Images Product_Slug slug')
                .sort({ createdAt: -1 })
                .skip(skip)
                .limit(limit);

            const normalizedReviews = reviews.map((review) => {
                const plain = review.toObject();
                if (plain.product) {
                    plain.product.Product_Slug = plain.product.Product_Slug || plain.product.slug || '';
                    plain.product_id = plain.product;
                }
                return plain;
            });

            return res.status(200).json({
                success: true,
                reviews: normalizedReviews,
                page,
                pages: Math.ceil(total / limit),
                total
            });
        }

        const productId = await resolveProductId(rawId);

        if (!productId) {
            return res.status(404).json({ message: "Product not found" });
        }

        // Pagination
        const page = parseInt(req.query.page, 10) || 1;
        const limit = parseInt(req.query.limit, 10) || 10;
        const startIndex = (page - 1) * limit;

        const total = await Review.countDocuments({ product: productId });

        const reviews = await Review.find({ product: productId })
            .populate('user', 'name avatar') // Assuming user has name and avatar
            .sort({ createdAt: -1 })
            .skip(startIndex)
            .limit(limit);

        res.status(200).json({
            success: true,
            count: reviews.length,
            total,
            pagination: {
                current: page,
                pages: Math.ceil(total / limit)
            },
            data: reviews
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// @desc    Create a review (with optional photo uploads via multer)
// @route   POST /api/v1/products/:id/reviews
// @access  Private (Requires Verified Purchase)
exports.createReview = async (req, res) => {
    try {
        const rawId = req.params.id;
        const productId = await resolveProductId(rawId);
        const userId = req.user.id;
        const { rating, title, comment } = req.body;

        if (!productId) {
            return res.status(404).json({ message: "Product not found" });
        }

        // 1. Check if user already reviewed
        const alreadyReviewed = await Review.findOne({ product: productId, user: userId });
        if (alreadyReviewed) {
            return res.status(400).json({ message: "You have already reviewed this product" });
        }

        // 2. Check for Verified Purchase
        const hasPurchased = await Order.findOne({
            user: userId,
            'items.product': productId,
            status: { $in: ['Delivered', 'Shipped', 'Completed'] }
        });

        if (!hasPurchased) {
            return res.status(403).json({
                message: "You can only review products you have purchased and received."
            });
        }

        // 3. Handle uploaded photos from multer (req.files)
        // Upload route should use: upload.array('images', 5)
        let uploadedImages = [];
        if (req.files && req.files.length > 0) {
            uploadedImages = req.files.map(file => `/uploads/${file.filename}`);
        }
        // Also accept pre-uploaded URL strings from req.body.images
        if (req.body.images) {
            const bodyImages = Array.isArray(req.body.images) ? req.body.images : [req.body.images];
            uploadedImages = [...uploadedImages, ...bodyImages];
        }

        const review = await Review.create({
            user: userId,
            product: productId,
            rating,
            title,
            comment,
            images: uploadedImages,
            verifiedPurchase: true
        });

        res.status(201).json({
            success: true,
            data: review
        });

    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};


// @desc    Update a review
// @route   PUT /api/v1/reviews/:id
// @access  Private (Owner only)
exports.updateReview = async (req, res) => {
    try {
        let review = await Review.findById(req.params.id);

        if (!review) {
            return res.status(404).json({ message: "Review not found" });
        }

        // Check ownership
        if (review.user.toString() !== req.user.id && req.user.role !== 'admin') {
            return res.status(401).json({ message: "Not authorized to update this review" });
        }

        review = await Review.findByIdAndUpdate(req.params.id, req.body, {
            new: true,
            runValidators: true
        });

        // Recalculate average manually or ensure hook runs (findByIdAndUpdate doesn't trigger save middleware)
        // We added a post findOneAndDelete hook, but for Update we might need to manually trigger static
        await Review.getAverageRating(review.product);

        res.status(200).json({
            success: true,
            data: review
        });

    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// @desc    Delete a review
// @route   DELETE /api/v1/reviews/:id
// @access  Private (Owner or Admin)
exports.deleteReview = async (req, res) => {
    try {
        const review = await Review.findById(req.params.id);

        if (!review) {
            return res.status(404).json({ message: "Review not found" });
        }

        // Check ownership
        if (review.user.toString() !== req.user.id && req.user.role !== 'admin') {
            return res.status(401).json({ message: "Not authorized to delete this review" });
        }

        await Review.findByIdAndDelete(req.params.id); // Triggers post hook we defined

        res.status(200).json({
            success: true,
            message: "Review removed"
        });

    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// @desc    Mark review as helpful
// @route   POST /api/v1/reviews/:id/helpful
// @access  Private
exports.markHelpful = async (req, res) => {
    try {
        const review = await Review.findById(req.params.id);
        const userId = req.user.id;

        if (!review) {
            return res.status(404).json({ message: "Review not found" });
        }

        if (review.helpful.includes(userId)) {
            // Unmark
            review.helpful = review.helpful.filter(id => id.toString() !== userId);
        } else {
            // Mark
            review.helpful.push(userId);
        }

        await review.save();

        res.status(200).json({
            success: true,
            data: review.helpful
        });

    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// @desc    Get current user's reviews
// @route   GET /api/v1/users/my-reviews
// @access  Private
exports.getMyReviews = async (req, res) => {
    try {
        const reviews = await Review.find({ user: req.user.id })
            .populate('product', 'Name Thumbnail_Images')
            .sort({ createdAt: -1 });

        res.status(200).json({
            success: true,
            count: reviews.length,
            data: reviews
        });

    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};
