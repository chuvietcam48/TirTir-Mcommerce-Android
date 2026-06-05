const express = require('express');
const router = express.Router();
const shippingController = require('../controllers/shipping.controller');
const { protect, authorize } = require('../middlewares/auth');
const rateLimit = require('express-rate-limit');

// Rate limit the public webhook endpoint to prevent abuse
const webhookLimiter = rateLimit({
    windowMs: 60 * 1000, // 1 minute
    max: 10,
    message: { success: false, error: 'Too many webhook requests' }
});

/**
 * @swagger
 * tags:
 *   name: Shipping
 *   description: GHN shipping integration endpoints
 */

/**
 * @swagger
 * /shipping/ghn-webhook:
 *   post:
 *     summary: GHN Webhook receiver (no JWT — called by GHN servers)
 *     tags: [Shipping]
 *     security: []
 *     description: |
 *       GHN calls this when order status changes (delivered, return, cancel, etc.)
 *       Validates via `Token` header = GHN_WEBHOOK_TOKEN env var.
 *     responses:
 *       200:
 *         description: Webhook received (always returns 200 to stop GHN retries)
 *       403:
 *         description: Invalid token
 */
router.post('/ghn-webhook', webhookLimiter, shippingController.handleGHNWebhook);

/**
 * @swagger
 * /shipping/simulate-delivery:
 *   post:
 *     summary: Simulate a GHN delivery event (Admin only — for testing)
 *     tags: [Shipping]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [orderId]
 *             properties:
 *               orderId: { type: string, description: "TirTir Order ID" }
 *               ghnStatus:
 *                 type: string
 *                 enum: [delivered, return, cancel]
 *                 default: delivered
 *     responses:
 *       200:
 *         description: Simulation successful
 */
router.post('/simulate-delivery', protect, authorize('admin'), shippingController.simulateGHNDelivery);

/**
 * @swagger
 * /shipping/shipped-orders:
 *   get:
 *     summary: List all orders with status Shipped (Admin only)
 *     tags: [Shipping]
 *     parameters:
 *       - in: query
 *         name: search
 *         schema: { type: string }
 *         description: Search by customer name or GHN order code
 *       - in: query
 *         name: page
 *         schema: { type: integer, default: 1 }
 *       - in: query
 *         name: limit
 *         schema: { type: integer, default: 20 }
 *     responses:
 *       200:
 *         description: List of shipped orders
 */
router.get('/shipped-orders', protect, authorize('admin', 'customer_service'), shippingController.getShippedOrders);

module.exports = router;
