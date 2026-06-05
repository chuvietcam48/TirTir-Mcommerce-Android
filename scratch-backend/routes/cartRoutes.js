const express = require('express');
const router = express.Router();
const { addToCart, getCart } = require('../controllers/cartController');
const { protect } = require('../middleware/authMiddleware');

router.post('/add', protect, addToCart);  // POST /api/v1/cart/add
router.get('/', protect, getCart);        // GET  /api/v1/cart

module.exports = router;
