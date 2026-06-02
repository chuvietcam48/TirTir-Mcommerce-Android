const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const {
    getWishlist,
    addToWishlist,
    removeFromWishlist,
    moveToCart,
    clearWishlist
} = require('../controllers/wishlist.controller');

// Tất cả route đều cần đăng nhập
router.use(protect);

router.get('/', getWishlist);
router.post('/add', addToWishlist);
router.delete('/remove/:productId', removeFromWishlist); // Dùng query ?shade=...
router.post('/move-to-cart', moveToCart);
router.delete('/clear', clearWishlist);

module.exports = router;