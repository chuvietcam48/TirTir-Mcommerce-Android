const express = require('express');
const router = express.Router();
const { protect } = require('../middleware/authMiddleware');
const loyaltyController = require('../backend/controllers/loyalty.controller');

router.get('/me', protect, loyaltyController.getLoyaltyDetails);
router.get('/history', protect, loyaltyController.getLoyaltyHistory);
router.post('/scan', protect, loyaltyController.scanBarcode);
router.get('/vouchers', loyaltyController.getVouchersList);
router.post('/redeem', protect, loyaltyController.redeemPoints);
router.get('/wallet', protect, loyaltyController.getWallet);

module.exports = router;
