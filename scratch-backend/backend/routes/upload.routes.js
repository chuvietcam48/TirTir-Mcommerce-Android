const express = require('express');
const router = express.Router();
const { protect, authorize } = require('../middlewares/auth');
const { uploadProductImage: productMiddleware, uploadBanner: bannerMiddleware, uploadReviewImage: reviewMiddleware } = require('../middlewares/upload.middleware');
const { uploadProductImage, uploadBanner, uploadImage } = require('../controllers/upload.controller');

// Upload Product Image (Admin only)
router.post('/product', protect, authorize('admin', 'inventory'), productMiddleware, uploadProductImage);

// Upload Banner Image (Admin only)
router.post('/banner', protect, authorize('admin'), bannerMiddleware, uploadBanner);

// Upload Review Image (Authenticated User)
// This maps to POST /api/v1/upload/image if client uses this generic endpoint for reviews
// Or specific endpoint: POST /api/v1/upload/review
router.post('/review', protect, reviewMiddleware, uploadImage);

// Generic Image Upload (Defaults to Product folder if productMiddleware used, or needs logic)
// For now, let's keep it specific. But user asked for POST /api/upload/image
// Let's make /image route that can handle products/reviews based on query param or default to products
router.post('/image', protect, authorize('admin', 'inventory'), productMiddleware, uploadImage); 

module.exports = router;
