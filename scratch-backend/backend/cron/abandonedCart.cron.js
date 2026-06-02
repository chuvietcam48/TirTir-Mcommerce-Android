const cron = require('node-cron');
const jwt = require('jsonwebtoken');
const Cart = require('../models/cart.model');
const sendEmail = require('../utils/sendEmail');
const logger = require('../utils/logger');
const Sentry = require('@sentry/node');
const { emitToAdmins } = require('../services/socket.service');

// Note: Bull/Redis queue is recommended here for scalability > 50 emails. 
// For now, using sequential processing with error catching.

/**
 * Abandoned Cart Recovery Cron Job
 * Runs every hour at minute 0
 */
const abandonedCartJob = cron.schedule('0 * * * *', async () => {
    logger.info('Running Abandoned Cart Check...');

    try {
        const now = new Date();
        const oneHourAgo = new Date(now.getTime() - 1 * 60 * 60 * 1000);
        const twentyFourHoursAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
        const seventyTwoHoursAgo = new Date(now.getTime() - 72 * 60 * 60 * 1000);
        const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);

        // Define the stages
        const stages = [
            {
                targetStatus: 'pending',
                newStatus: 'email_1_sent',
                timeTarget: { $lte: oneHourAgo, $gt: twentyFourHoursAgo }, // Before 1h, but newer than 24h
                subject: 'You left something behind! 🛒',
                introText: 'your beauty favorites are waiting!',
                metricField: 'email1SentAt'
            },
            {
                targetStatus: 'email_1_sent',
                newStatus: 'email_2_sent',
                timeTarget: { $lte: twentyFourHoursAgo, $gt: seventyTwoHoursAgo }, // Before 24h, newer than 72h
                subject: 'Don\'t miss out on your beauty favorites! ⏰',
                introText: 'items in your cart are selling fast. Secure them before they are gone!',
                metricField: 'email2SentAt'
            },
            {
                targetStatus: 'email_2_sent',
                newStatus: 'email_3_sent',
                timeTarget: { $lte: seventyTwoHoursAgo, $gt: sevenDaysAgo }, // Before 72h, newer than 7 days
                subject: 'Final chance! Special offer inside 🎁',
                introText: 'this is your last chance to grab the items you left behind. Come back now and enjoy free shipping or a surprise discount!',
                metricField: 'email3SentAt'
            }
        ];

        // Release carts stuck processing for > 2 hours (guards against crashed mid-run)
        await Cart.updateMany(
            { isProcessing: true, updatedAt: { $lt: new Date(Date.now() - 2 * 60 * 60 * 1000) } },
            { $set: { isProcessing: false } }
        );

        let totalSent = 0;

        for (const stage of stages) {
            const cartsToProcess = await Cart.find({
                $and: [
                    { recoveryStatus: stage.targetStatus },
                    { recoveryStatus: { $ne: 'unsubscribed' } }
                ],
                lastAbandonedAt: stage.timeTarget,
                'items.0': { $exists: true },
                isProcessing: { $ne: true }
            }).populate('user', 'email name').populate('items.product', 'Name Price');

            if (cartsToProcess.length > 0) {
                logger.info(`Found ${cartsToProcess.length} carts for stage ${stage.newStatus}`);
                
                for (const cart of cartsToProcess) {
                    if (!cart.user || !cart.user.email) continue;
                    
                    let productsHtml = '<ul>';
                    let productsText = '';
                    cart.items.forEach(item => {
                        const productName = item.product ? item.product.Name : 'Product';
                        productsHtml += `<li>${productName} (Qty: ${item.quantity}) - $${item.price}</li>`;
                        productsText += `- ${productName} (Qty: ${item.quantity}) - $${item.price}\n`;
                    });
                    productsHtml += '</ul>';

                    // Using a dummy token for the recovery link as an example, this can be implemented in auth controller later
                    const recoveryToken = Buffer.from(cart.user.email).toString('base64');
                    const recoveryUrl = `${process.env.FRONTEND_URL || 'http://localhost:4200'}/cart?recover=${recoveryToken}`;

                    const unsubscribeToken = jwt.sign(
                        { userId: cart.user._id, cartId: cart._id },
                        process.env.JWT_SECRET,
                        { expiresIn: '7d' }
                    );
                    const unsubscribeUrl = `${process.env.BACKEND_URL}/api/cart/unsubscribe?token=${unsubscribeToken}`;

                    const emailOptions = {
                        email: cart.user.email,
                        subject: stage.subject,
                        message: `Hey ${cart.user.name || 'there'}, ${stage.introText} ✨\n\nWe noticed you left the following items in your cart:\n${productsText}\nCome back and complete your purchase!\n${recoveryUrl}`,
                        html: `
                            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                                <h2>Hey ${cart.user.name || 'there'}!</h2>
                                <p>${stage.introText} ✨</p>
                                <p>We noticed you left the following items in your cart:</p>
                                ${productsHtml}
                                <br>
                                <a href="${recoveryUrl}" style="background-color: #ff4d4f; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Return to Cart</a>
                                <p style="text-align:center;font-size:11px;color:#999;margin-top:32px">Don't want reminders? <a href="${unsubscribeUrl}" style="color:#999;text-decoration:underline">Unsubscribe</a></p>
                            </div>
                        `
                    };

                    // Atomically claim the cart — prevents duplicate sends on concurrent/restarted runs
                    const claimed = await Cart.findOneAndUpdate(
                        { _id: cart._id, isProcessing: { $ne: true } },
                        { $set: { isProcessing: true } },
                        { new: true }
                    );
                    if (!claimed) {
                        logger.warn(`Cart ${cart._id} skipped — already being processed`);
                        continue;
                    }

                    try {
                        await sendEmail(emailOptions);

                        cart.recoveryStatus = stage.newStatus;
                        // Initialize recoveryMetrics if it doesn't exist yet
                        if (!cart.recoveryMetrics) cart.recoveryMetrics = {};
                        cart.recoveryMetrics[stage.metricField] = new Date();
                        await cart.save();

                        totalSent++;
                        logger.info(`Recovery email (${stage.newStatus}) sent to ${cart.user.email}`);

                        // ── Real-time: notify admins a cart recovery email was sent ──
                        emitToAdmins('cart_recovered', {
                            type: 'cart_recovered',
                            title: 'Cart Recovered',
                            message: `A cart was emailed for recovery via ${stage.newStatus}`,
                            cartId: cart._id
                        });
                    } catch (emailError) {
                        logger.error(`Failed to send email to ${cart.user.email}: ${emailError.message}`);
                    } finally {
                        await Cart.findByIdAndUpdate(cart._id, { $set: { isProcessing: false } });
                    }
                }
            }
        }

        // Final cleanup phase: Mark carts older than 7 days as abandoned_final
        const finalAbandonedCarts = await Cart.updateMany(
            { 
                recoveryStatus: 'email_3_sent',
                lastAbandonedAt: { $lte: sevenDaysAgo }
            },
            {
                $set: { recoveryStatus: 'abandoned_final' }
            }
        );

        if (finalAbandonedCarts.modifiedCount > 0) {
            logger.info(`Marked ${finalAbandonedCarts.modifiedCount} carts as abandoned_final.`);
        }

        logger.info(`Abandoned Cart Check finished. Sent ${totalSent} emails.`);

    } catch (error) {
        logger.error('Error in Abandoned Cart Cron Job:', error);
        Sentry.captureException(error);
    }
});

module.exports = abandonedCartJob;
