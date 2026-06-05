/**
 * GHN Status Polling Job — Fallback for missed webhooks
 *
 * Runs every 30 minutes (configurable via GHN_POLL_INTERVAL env).
 * Queries GHN for all orders that are still "Shipped" in our system
 * and updates any that GHN marks as delivered/returned/cancelled.
 *
 * This is a safety net — the webhook is the primary mechanism.
 */

const cron = require('node-cron');
const axios = require('axios');
const Order = require('../models/order.model');
const logger = require('../utils/logger');

const GHN_API_BASE = 'https://dev-online-gateway.ghn.vn/shiip/public-api';
const GHN_API_KEY = process.env.GHN_API_KEY;
const GHN_SHOP_ID = process.env.GHN_SHOP_ID;

// Mirror of mapping in shipping.controller.js
const GHN_STATUS_MAP = {
    'delivered': 'Delivered',
    'return': 'Cancelled',
    'cancel': 'Cancelled',
};

const pollGHNStatus = async () => {
    // Skip if GHN not configured (dev/sim mode)
    if (!GHN_API_KEY || !GHN_SHOP_ID) {
        logger.debug('[GHN Cron] No GHN credentials — skipping poll.');
        return;
    }

    logger.info('[GHN Cron] Starting status poll for Shipped orders...');

    try {
        // Find all Shipped orders that have a GHN order code
        const shippedOrders = await Order.find({
            status: 'Shipped',
            ghnOrderCode: { $ne: null }
        }).select('_id ghnOrderCode ghnProcessedEvents');

        if (shippedOrders.length === 0) {
            logger.info('[GHN Cron] No Shipped orders to poll.');
            return;
        }

        const { updateOrderStatusInternal } = require('../controllers/shipping.controller');

        for (const order of shippedOrders) {
            try {
                const response = await axios.get(
                    `${GHN_API_BASE}/v2/shipping-order/detail`,
                    {
                        headers: { 'Token': GHN_API_KEY, 'ShopId': GHN_SHOP_ID },
                        params: { order_code: order.ghnOrderCode },
                        timeout: 8000
                    }
                );

                const ghnStatus = response.data?.data?.status?.toLowerCase();
                const newStatus = GHN_STATUS_MAP[ghnStatus];

                if (newStatus) {
                    logger.info(`[GHN Cron] ${order.ghnOrderCode}: GHN=${ghnStatus} → updating to ${newStatus}`);
                    await updateOrderStatusInternal(order._id, newStatus, null, 'cron');
                }

            } catch (orderErr) {
                // Don't crash the loop on a single order failure
                logger.warn(`[GHN Cron] Failed to poll order ${order.ghnOrderCode}: ${orderErr.message}`);
            }

            // Small delay between GHN API calls to avoid rate limiting
            await new Promise(r => setTimeout(r, 300));
        }

        logger.info('[GHN Cron] Poll complete.');
    } catch (err) {
        logger.error(`[GHN Cron] Fatal error: ${err.message}`);
    }
};

/**
 * Start the polling cron job.
 * Default: every 30 minutes.
 */
const startGHNPollingJob = () => {
    const interval = process.env.GHN_POLL_INTERVAL || '*/30 * * * *';
    logger.info(`[GHN Cron] Scheduling status poll: "${interval}"`);
    cron.schedule(interval, pollGHNStatus);
};

module.exports = { startGHNPollingJob, pollGHNStatus };
