const firebaseAdmin = require('./firebaseAdmin.service');

/**
 * Send FCM push notification containing voucher details to a specific user (At Risk)
 */
async function sendVoucherFcmToUser(userId, voucher) {
    console.log(`[BE2][VOUCHER_FCM] Request to send voucher FCM: user=${userId}, code=${voucher?.voucherCode || voucher?.code}`);
    
    if (!firebaseAdmin.isFirebaseEnabled()) {
        const msg = 'Firebase Admin is disabled. Skipping voucher FCM.';
        console.warn(`[BE2][VOUCHER_FCM] ${msg}`);
        return { success: false, warning: msg };
    }

    try {
        const result = await firebaseAdmin.sendVoucherPush(userId, voucher);
        if (result && result.successCount > 0) {
            console.log(`[BE2][VOUCHER_FCM] Voucher push sent successfully to user ${userId}`);
            return { success: true, result };
        } else {
            const reason = result?.reason || 'No active device tokens found for user';
            console.warn(`[BE2][VOUCHER_FCM] Voucher push skipped: ${reason}`);
            return { success: false, warning: reason };
        }
    } catch (err) {
        console.error(`[BE2][VOUCHER_FCM] Error sending voucher FCM:`, err.message);
        return { success: false, error: err.message };
    }
}

module.exports = {
    sendVoucherFcmToUser
};
