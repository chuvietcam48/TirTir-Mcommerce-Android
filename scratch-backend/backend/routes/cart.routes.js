const express = require('express');
const router = express.Router();
const {
    addToCart,
    getCart,
    updateCartItem,
    removeFromCart,
    clearCart,
    getCartCount,
    unsubscribeRecovery,
    mergeCart,
    abandonCart
} = require('../controllers/cart.controller');
const { protect } = require('../middlewares/auth');

// Public route — no auth needed (linked from email)
router.get('/unsubscribe', unsubscribeRecovery);

// Apply protection to all remaining cart routes
router.use(protect);

router.post('/add', addToCart);
router.get('/', getCart);
router.put('/update', updateCartItem);
router.delete('/remove/:productId/:shade', removeFromCart);
router.delete('/clear', clearCart);
router.get('/count', getCartCount);
router.post('/merge', mergeCart);
router.post('/abandon', abandonCart);

module.exports = router;
