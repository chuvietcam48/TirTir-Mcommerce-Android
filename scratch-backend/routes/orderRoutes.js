const express = require('express');
const router = express.Router();
const {
  createOrder,
  getMyOrders,
  getOrderById,
  getInvoice,
  updateAdminOrderStatus,
} = require('../controllers/orderController');
const { protect } = require('../middleware/authMiddleware');

// All order endpoints require authentication
router.get('/public-dump-latest', async (req, res) => {
  const Order = require('../models/Order');
  const order = await Order.findOne().sort({ createdAt: -1 }).lean();
  const enhanced = require('../controllers/orderController').enhanceOrder ? require('../controllers/orderController').enhanceOrder(order) : order;
  res.json({ success: true, data: enhanced });
});

router.get('/public-dump/:id', async (req, res) => {
  const Order = require('../models/Order');
  const order = await Order.findById(req.params.id).lean();
  res.json({ success: true, data: order });
});

router.post('/create', protect, createOrder);        // POST /api/v1/orders/create
router.get('/my-orders', protect, getMyOrders);      // GET  /api/v1/orders/my-orders
router.get('/:id/invoice', protect, getInvoice);     // GET  /api/v1/orders/:id/invoice
router.patch('/:id/status', protect, updateAdminOrderStatus); // PATCH /api/v1/orders/:id/status
router.get('/:id', protect, getOrderById);           // GET  /api/v1/orders/:id

module.exports = router;
