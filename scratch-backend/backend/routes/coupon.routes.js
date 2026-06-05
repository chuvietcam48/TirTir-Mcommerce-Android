const express = require('express');
const router = express.Router();
const {
    createCoupon,
    getAllCoupons,
    getActiveCoupons,
    validateCoupon,
    applyCoupon,
    updateCoupon,
    deleteCoupon,
    getCouponById,
    toggleCouponStatus,
    getCouponStats
} = require('../controllers/coupon.controller');

const { protect, authorize } = require('../middlewares/auth');

// Public routes
router.get('/active', getActiveCoupons);
router.post('/validate', validateCoupon);

// Protected routes (require authentication)
router.post('/apply', protect, applyCoupon);

// Admin routes (require authentication + admin role)
router.post('/', protect, authorize('admin'), createCoupon);
router.get('/', protect, authorize('admin'), getAllCoupons);
router.get('/stats', protect, authorize('admin'), getCouponStats); // Place before /:id
router.get('/:id', protect, authorize('admin'), getCouponById);
router.put('/:id', protect, authorize('admin'), updateCoupon);
router.patch('/:id/status', protect, authorize('admin'), toggleCouponStatus);
router.delete('/:id', protect, authorize('admin'), deleteCoupon);

module.exports = router;
