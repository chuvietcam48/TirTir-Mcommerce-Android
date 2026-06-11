const express = require('express');
const router = express.Router();
const { getLoyaltyDetails, getLoyaltyHistory } = require('../controllers/loyalty.controller');
const { protect } = require('../middlewares/auth');

router.use(protect); // Require authentication for all loyalty routes

router.get('/me', getLoyaltyDetails);
router.get('/history', getLoyaltyHistory);

module.exports = router;
