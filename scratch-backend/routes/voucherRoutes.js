const express = require('express');
const router = express.Router();
const { getUserVouchers, validateVoucher } = require('../controllers/voucherController');
const { protect } = require('../middleware/authMiddleware');

router.get('/my-vouchers', protect, getUserVouchers);
router.post('/validate', protect, validateVoucher);

module.exports = router;
