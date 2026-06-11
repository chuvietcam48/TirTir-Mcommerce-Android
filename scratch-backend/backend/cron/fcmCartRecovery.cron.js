const cron = require('node-cron');
const { runCartRecovery } = require('../services/cartRecoveryFcm.service');

// Cron schedule: hourly by default, configurable via environment
const cronSchedule = process.env.CART_RECOVERY_INTERVAL_CRON || '0 * * * *';

let fcmRecoveryJob = null;

if (process.env.ENABLE_CRON !== 'false') {
    fcmRecoveryJob = cron.schedule(cronSchedule, async () => {
        console.log('[BE2][CART_RECOVERY] Running automated FCM Cart Recovery cron job...');
        try {
            const stats = await runCartRecovery();
            // Logging results matching format in requirements
            console.log(`[BE2][CART_RECOVERY] Cron Run Finished. Scanned=${stats.scanned}, Sent=${stats.sent}, Skipped=${stats.skipped}, Errors=${stats.errors}`);
        } catch (err) {
            console.error('[BE2][CART_RECOVERY] Cron job error:', err.message);
        }
    });
    console.log(`[BE2][CART_RECOVERY] Cron job registered with schedule: ${cronSchedule}`);
} else {
    console.log('[BE2][CART_RECOVERY] Cron job is disabled by ENABLE_CRON env flag.');
}

module.exports = fcmRecoveryJob;
