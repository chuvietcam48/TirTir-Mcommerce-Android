const express = require('express');
const router = express.Router();
const paymentController = require('../controllers/payment.controller');
const { protect } = require('../middlewares/auth.js');

// const { protect } = require('../middlewares/auth'); // Uncomment nếu cần bảo vệ

// POST /api/payments/create-url
router.post('/create-url', protect, paymentController.createPaymentUrl);

// Hủy đơn & Tự động hoàn tiền
router.post('/cancel_refund/:orderId', protect, paymentController.cancelAndRefundOrder);

// GET /api/payments/vnpay-return (Callback từ VNPay)
router.get('/vnpay-return', paymentController.vnpayReturn);

// GET /api/payments/vnpay-ipn (Webhook gọi ngầm từ VNPay)
router.get('/vnpay-ipn', paymentController.vnpayIPN);

// GET /api/payments/momo-return
router.get('/momo-return', paymentController.momoReturn);

// POST /api/payments/momo-ipn (Lưu ý MoMo dùng POST cho IPN)
router.post('/momo-ipn', paymentController.momoIPN);

module.exports = router;