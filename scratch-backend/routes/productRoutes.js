const express = require('express');
const router = express.Router();
const { getProducts, getProductById } = require('../controllers/productController');

// GET /api/v1/products?limit=100&category=cleanser
router.get('/', getProducts);

// GET /api/v1/products/:id
router.get('/:id', getProductById);

module.exports = router;
