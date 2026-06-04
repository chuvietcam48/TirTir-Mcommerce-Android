const express = require('express');
const router = express.Router();
const {
  createOrder,
  getMyOrders,
  getOrderById,
  getInvoice,
} = require('../controllers/orderController');
const { protect } = require('../middleware/authMiddleware');

// All order endpoints require authentication
router.post('/create', protect, createOrder);        // POST /api/v1/orders/create
router.get('/my-orders', protect, getMyOrders);      // GET  /api/v1/orders/my-orders
router.get('/:id/invoice', protect, getInvoice);     // GET  /api/v1/orders/:id/invoice
router.get('/:id', protect, getOrderById);           // GET  /api/v1/orders/:id

module.exports = router;
