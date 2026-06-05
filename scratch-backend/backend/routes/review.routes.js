const express = require('express');
const router = express.Router({ mergeParams: true });
const {
    getProductReviews,
    createReview,
    updateReview,
    deleteReview,
    markHelpful,
    getMyReviews,
    getAllReviewsAdmin,
    getReviewByIdAdmin
} = require('../controllers/review.controller');
const { protect, authorize } = require('../middlewares/auth');
const { reviewLimiter } = require('../middlewares/rateLimit');

// Note: Using mergeParams: true allows us to access :id from product routes if we mount it there

// Admin Route - Get All Reviews (Must be before /:id)
router.get('/admin/all', protect, authorize('admin'), getAllReviewsAdmin);
router.get('/:id', protect, authorize('admin'), getReviewByIdAdmin);

// Public routes
router.get('/', getProductReviews);


// Protected routes
router.use(protect);

router.post('/', reviewLimiter, createReview);
router.put('/:id', updateReview);
router.delete('/:id', deleteReview);
router.post('/:id/helpful', markHelpful);

module.exports = router;
