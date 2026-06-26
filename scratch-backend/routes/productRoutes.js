const express = require('express');
const router = express.Router();
const { getProducts, getProductById, matchCushion } = require('../controllers/productController');

// GET /api/v1/products/cushion-match?skin_tone_hex=#D8A087
router.get('/cushion-match', matchCushion);

// GET /api/v1/products?limit=100&category=cleanser
router.get('/', getProducts);

// GET /api/v1/products/:id
router.get('/:id', getProductById);

module.exports = router;
