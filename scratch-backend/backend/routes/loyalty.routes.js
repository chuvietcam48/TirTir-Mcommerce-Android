const express = require('express');
const router = express.Router();
const { 
    getLoyaltyDetails, 
    getLoyaltyHistory, 
    scanBarcode, 
    getVouchersList, 
    redeemPoints, 
    getWallet,
    claimWelcomeVoucher
} = require('../controllers/loyalty.controller');
const { protect } = require('../middlewares/auth');

router.use(protect); // Require authentication for all loyalty routes

router.get('/me', getLoyaltyDetails);
router.get('/history', getLoyaltyHistory);
router.post('/scan', scanBarcode);
router.get('/vouchers', getVouchersList);
router.post('/redeem', redeemPoints);
router.get('/wallet', getWallet);
router.post('/claim-welcome', claimWelcomeVoucher);

module.exports = router;
