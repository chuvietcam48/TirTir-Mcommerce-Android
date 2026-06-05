/**
 * Shipping Controller — GHN Webhook Integration
 *
 * Endpoints:
 *  POST /api/v1/shipping/ghn-webhook          No JWT — GHN calls this
 *  POST /api/v1/shipping/simulate-delivery    Admin only — for demo/testing
 *  GET  /api/v1/shipping/shipped-orders       Admin only — list Shipped orders
 */

const axios = require('axios');
const Order = require('../models/order.model');
const logger = require('../utils/logger');
const { createNotification } = require('./notification.controller');

// ─── GHN Config ───────────────────────────────────────────────────────────────
const GHN_API_BASE = 'https://dev-online-gateway.ghn.vn/shiip/public-api'; // Sandbox
const GHN_SHOP_ID = process.env.GHN_SHOP_ID;
const GHN_API_KEY = process.env.GHN_API_KEY;
const GHN_WEBHOOK_TOKEN = process.env.GHN_WEBHOOK_TOKEN || '';

/**
 * GHN status → TirTir status mapping.
 * null = ignore (don't update order).
 */
const GHN_STATUS_MAP = {
    'delivered': 'Delivered',
    'return': 'Cancelled',  // GHN hoàn hàng → Cancelled + restock
    'cancel': 'Cancelled',  // GHN hủy → Cancelled + restock
    // Ignored intermediate states
    'ready_to_pick': null,
    'picking': null,
    'delivering': null,
    'waiting_to_return': null,
};

const appendStatusHistory = (order, status, note = '') => {
    const history = Array.isArray(order.statusHistory) ? order.statusHistory : [];
    const last = history[history.length - 1];
    if (last?.status === status) return;
    history.push({
        status,
        timestamp: new Date(),
        note: (note || '').trim()
    });
    order.statusHistory = history;
};

// ─── Internal Order Status Updater ────────────────────────────────────────────
/**
 * Shared logic for updating an order status.
 * Handles stock management for Cancelled orders (from GHN return/cancel).
 * Called both by Admin manual update and GHN webhook.
 */
const updateOrderStatusInternal = async (orderId, newStatus, performedById = null, eventSource = 'admin') => {
    const Product = require('../models/product.model');
    const StockHistory = require('../models/stock.history.model');

    const order = await Order.findById(orderId).populate('items.product');
    if (!order) throw new Error(`Order ${orderId} not found`);

    const oldStatus = order.status;

    // Guard: don't downgrade a Delivered order (race condition protection)
    if (oldStatus === 'Delivered' || oldStatus === 'Cancelled') {
        logger.warn(`[GHN] Ignoring status update for order ${orderId}: already ${oldStatus}`);
        return null;
    }

    // ─── Restock on Cancelled (from Shipped or Processing) ───────────────────
    if (newStatus === 'Cancelled' && (oldStatus === 'Processing' || oldStatus === 'Shipped')) {
        for (const item of order.items) {
            if (!item.product) continue;
            const product = await Product.findByIdAndUpdate(item.product._id, {
                $inc: { Stock_Quantity: item.quantity }
            }, { new: true });

            if (product) {
                await StockHistory.create({
                    product: product._id,
                    action: 'Refund',
                    change_type: 'Increase',
                    source_id: orderId.toString(),
                    balance_before: product.Stock_Quantity - item.quantity,
                    balance_after: product.Stock_Quantity,
                    changeAmount: item.quantity,
                    reason: `GHN ${eventSource === 'ghn' ? 'Return/Cancel' : 'Admin Cancel'} — Restock`,
                    performedBy: performedById
                });
            }
        }
    }

    // ─── Update Order Status ──────────────────────────────────────────────────
    order.status = newStatus;
    appendStatusHistory(order, newStatus, `Updated from ${eventSource.toUpperCase()}`);
    await order.save();

    // ─── Send Notification to User ────────────────────────────────────────────
    const shortId = orderId.toString().slice(-6).toUpperCase();
    const notifMap = {
        Delivered: {
            title: 'Order Delivered',
            message: `Order #${shortId} has been delivered to you. Thank you for shopping with TirTir!`
        },
        Cancelled: {
            title: 'Order Cancelled',
            message: `Order #${shortId} has been cancelled or returned by the carrier.`
        }
    };

    const notif = notifMap[newStatus];
    if (notif && order.user) {
        await createNotification(
            order.user.toString(),
            'order',           // type
            notif.title,       // title
            notif.message,     // message
            `/account/orders`  // link
        );
    }

    logger.info(`[Shipping] Order ${shortId}: ${oldStatus} → ${newStatus} (source: ${eventSource})`);
    return order;
};

// ─── Create GHN Order (when Admin marks Shipped) ──────────────────────────────
const createGHNOrder = async (order) => {
    // Skip if no GHN credentials configured (dev/demo mode)
    if (!GHN_API_KEY || !GHN_SHOP_ID) {
        logger.warn('[GHN] GHN_API_KEY not set — skipping real GHN API call. Using mock order code.');
        const mockCode = `GHN-MOCK-${order._id.toString().slice(-6).toUpperCase()}`;
        await Order.findByIdAndUpdate(order._id, { ghnOrderCode: mockCode });
        return mockCode;
    }

    try {
        const payload = {
            payment_type_id: 2,
            note: `TirTir Order #${order._id.toString().slice(-6).toUpperCase()}`,
            required_note: 'KHONGCHOXEMHANG',
            to_name: order.shippingAddress.fullName,
            to_phone: order.shippingAddress.phone,
            to_address: order.shippingAddress.address,
            to_ward_name: order.shippingAddress.ward || '',
            to_district_name: order.shippingAddress.district || order.shippingAddress.city,
            to_province_name: order.shippingAddress.city,
            cod_amount: 0, // 0 because all payments are prepaid (no COD)
            weight: 500,
            length: 20,
            width: 15,
            height: 10,
            service_type_id: 2,
            items: order.items.map(i => ({
                name: i.name,
                code: i.product?.toString() || '',
                quantity: i.quantity,
                price: Math.round(i.price)
            }))
        };

        const response = await axios.post(
            `${GHN_API_BASE}/v2/shipping-order/create`,
            payload,
            {
                headers: {
                    'Token': GHN_API_KEY,
                    'ShopId': GHN_SHOP_ID.toString(),
                    'Content-Type': 'application/json'
                },
                timeout: 10000
            }
        );

        const ghnOrderCode = response.data?.data?.order_code;
        if (ghnOrderCode) {
            await Order.findByIdAndUpdate(order._id, { ghnOrderCode });
            logger.info(`[GHN] Created shipping order ${ghnOrderCode} for order ${order._id}`);
        }
        return ghnOrderCode;

    } catch (err) {
        // On failure: log + rollback status back to Processing
        logger.error(`[GHN] Failed to create GHN order for ${order._id}: ${err.message}`);
        // Rollback: revert to Processing so Admin knows it failed
        await Order.findByIdAndUpdate(order._id, { status: 'Processing' });
        // Rethrow so the route handler can inform Admin
        const error = new Error(`GHN API Error: ${err.response?.data?.message || err.message}. Order reverted to Processing.`);
        error.statusCode = 502;
        throw error;
    }
};

// ─── Endpoint: GHN Webhook (no JWT) ─────────────────────────────────────────
exports.handleGHNWebhook = async (req, res) => {
    // 1. Validate Token
    const token = req.headers['token'];
    if (!GHN_WEBHOOK_TOKEN || token !== GHN_WEBHOOK_TOKEN) {
        logger.warn(`[GHN Webhook] Invalid token received: ${token}`);
        return res.status(403).json({ message: 'Forbidden' });
    }

    // 2. Respond 200 immediately so GHN won't retry
    res.json({ success: true, message: 'Webhook received' });

    // 3. Process async after response
    setImmediate(async () => {
        try {
            const { OrderCode, Status, event_id } = req.body;
            logger.info(`[GHN Webhook] Received: OrderCode=${OrderCode} Status=${Status} event_id=${event_id}`);

            // Find TirTir order by ghnOrderCode
            const order = await Order.findOne({ ghnOrderCode: OrderCode });
            if (!order) {
                logger.warn(`[GHN Webhook] No order found with ghnOrderCode=${OrderCode}`);
                return;
            }

            // Idempotency: skip if event already processed
            if (event_id && order.ghnProcessedEvents.includes(event_id)) {
                logger.info(`[GHN Webhook] Event ${event_id} already processed, skipping.`);
                return;
            }

            // Map GHN status to TirTir status
            const newStatus = GHN_STATUS_MAP[Status?.toLowerCase()];
            if (!newStatus) {
                logger.info(`[GHN Webhook] Ignoring untracked GHN status: ${Status}`);
                return;
            }

            // Update order + save event_id
            await updateOrderStatusInternal(order._id, newStatus, null, 'ghn');
            if (event_id) {
                await Order.findByIdAndUpdate(order._id, {
                    $push: { ghnProcessedEvents: event_id }
                });
            }

        } catch (err) {
            logger.error(`[GHN Webhook] Processing error: ${err.message}`);
        }
    });
};

// ─── Endpoint: Simulate GHN Delivery (Admin Only) ────────────────────────────
exports.simulateGHNDelivery = async (req, res) => {
    try {
        const { orderId, ghnStatus = 'delivered' } = req.body;

        if (!orderId) {
            return res.status(400).json({ success: false, message: 'orderId is required' });
        }

        const newStatus = GHN_STATUS_MAP[ghnStatus?.toLowerCase()];
        if (newStatus === undefined) {
            return res.status(400).json({
                success: false,
                message: `Unknown GHN status: "${ghnStatus}". Valid: ${Object.keys(GHN_STATUS_MAP).join(', ')}`
            });
        }
        if (newStatus === null) {
            return res.status(400).json({
                success: false,
                message: `GHN status "${ghnStatus}" is an intermediate state — no order update triggered.`
            });
        }

        const updatedOrder = await updateOrderStatusInternal(orderId, newStatus, req.user.id, 'simulator');
        if (!updatedOrder) {
            return res.status(409).json({
                success: false,
                message: 'Order is already in a terminal state (Delivered or Cancelled).'
            });
        }

        res.json({
            success: true,
            message: `Simulated GHN "${ghnStatus}" → Order is now "${newStatus}"`,
            order: {
                _id: updatedOrder._id,
                status: updatedOrder.status,
                ghnOrderCode: updatedOrder.ghnOrderCode
            }
        });

    } catch (err) {
        logger.error('[Simulate GHN] Error:', err.message);
        res.status(err.statusCode || 500).json({ success: false, message: err.message });
    }
};

// ─── Endpoint: Get Shipped Orders (Admin Only) ────────────────────────────────
exports.getShippedOrders = async (req, res) => {
    try {
        const { search = '', page = 1, limit = 20 } = req.query;
        const skip = (page - 1) * limit;

        const query = { status: 'Shipped' };

        // Search by order ID suffix or customer name
        if (search) {
            const searchRegex = new RegExp(search, 'i');
            query.$or = [
                { ghnOrderCode: searchRegex },
                { 'shippingAddress.fullName': searchRegex }
            ];
        }

        const [orders, total] = await Promise.all([
            Order.find(query)
                .populate('user', 'name email')
                .select('_id status ghnOrderCode shippingAddress totalAmount createdAt user')
                .sort({ createdAt: -1 })
                .skip(skip)
                .limit(Number(limit)),
            Order.countDocuments(query)
        ]);

        res.json({ success: true, data: orders, total, page: Number(page), limit: Number(limit) });

    } catch (err) {
        logger.error('[GetShippedOrders] Error:', err.message);
        res.status(500).json({ success: false, message: err.message });
    }
};

// Export internal helpers for use in order.controller.js
exports.createGHNOrder = createGHNOrder;
exports.updateOrderStatusInternal = updateOrderStatusInternal;
