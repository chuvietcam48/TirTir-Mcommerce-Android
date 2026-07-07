const express = require('express');
const router = express.Router();
const { getProducts, getProductById, matchCushion, createProduct, updateProduct, deleteProduct } = require('../controllers/productController');
const { getProductReviews } = require('../controllers/reviewController');
const { protect, restrictTo } = require('../middleware/authMiddleware');

// GET /api/v1/products/cushion-match?skin_tone_hex=#D8A087
router.get('/cushion-match', matchCushion);

// GET /api/v1/products?limit=100&category=cleanser
router.get('/', getProducts);

// GET /api/v1/products/:id
router.get('/:id', getProductById);

// GET /api/v1/products/:id/reviews
router.get('/:id/reviews', getProductReviews);

// POST /api/v1/products (Admin only)
router.post('/', protect, restrictTo('admin'), createProduct);

// PUT /api/v1/products/:id (Admin only)
router.put('/:id', protect, restrictTo('admin'), updateProduct);

// DELETE /api/v1/products/:id (Admin only)
router.delete('/:id', protect, restrictTo('admin'), deleteProduct);

module.exports = router;
