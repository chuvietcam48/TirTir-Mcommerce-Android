const express  = require('express');
const router   = express.Router();
const { arbitrate, vnpayReturn, vnpayIpn } = require('../controllers/paymentController');
const { protect } = require('../middleware/authMiddleware');

// POST /api/v1/payments/arbitrate  — requires login
router.post('/arbitrate', protect, arbitrate);

// VNPAY redirect / IPN — called by VNPAY, no JWT
router.get('/vnpay-return', vnpayReturn);
router.get('/vnpay-ipn',    vnpayIpn);   // VNPAY uses GET for IPN

module.exports = router;
