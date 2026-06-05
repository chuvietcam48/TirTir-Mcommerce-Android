const express = require('express');
const {
    sendFlashSale,
    recoverAbandonedCarts
} = require('../controllers/marketing.controller');

const router = express.Router();

// Middleware protection
const { protect, authorize } = require('../middlewares/auth');

// Protect all routes
router.use(protect);
router.use(authorize('admin')); // Only admin can trigger marketing blasts

router.post('/flash-sale', sendFlashSale);
router.post('/abandoned-cart-recovery', recoverAbandonedCarts);

module.exports = router;
