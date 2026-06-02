const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const { uploadAvatar: uploadMiddleware } = require('../middlewares/upload.middleware');
const {
    getProfile,
    updateProfile,
    changePassword,
    uploadAvatar,
    getAddresses,
    addAddress,
    updateAddress,
    deleteAddress,
    setDefaultAddress
} = require('../controllers/user.controller');
const { getMyReviews } = require('../controllers/review.controller');
const { updateProfileValidator, changePasswordValidator, addressValidator } = require('../validators/user.validator');
const { validate } = require('../middlewares/validate');

/**
 * User Profile & Address Management Routes
 * All routes are protected - require authentication
 */

// ===== PROFILE ROUTES =====
router.get('/my-reviews', protect, getMyReviews);

router.route('/profile')
    .get(protect, getProfile)
    .put(protect, updateProfileValidator, validate, updateProfile);

router.post('/change-password', protect, changePasswordValidator, validate, changePassword);

// Avatar upload route
router.post('/avatar/upload', protect, uploadMiddleware, uploadAvatar);

// ===== ADDRESS ROUTES =====
router.route('/addresses')
    .get(protect, getAddresses)
    .post(protect, addressValidator, validate, addAddress);

router.route('/addresses/:id')
    .put(protect, addressValidator, validate, updateAddress)
    .delete(protect, deleteAddress);

router.patch('/addresses/:id/set-default', protect, setDefaultAddress);

module.exports = router;
