const firebaseAdmin = require('./firebaseAdmin.service');
const User = require('../models/user.model');

/**
 * Get active FCM tokens for a Firebase UID from MongoDB or Firestore
 */
async function getTokensForFirebaseUid(firebaseUid) {
    let tokens = [];

    // 1. Try to fetch from MongoDB User model
    try {
        const mongoUser = await User.findOne({ firebaseUid });
        if (mongoUser && mongoUser.fcmTokens) {
            tokens = mongoUser.fcmTokens.filter(t => t.active !== false).map(t => t.token);
        }
    } catch (err) {
        console.error('[BE2][CART_RECOVERY] MongoDB token fetch error:', err.message);
    }

    if (tokens.length > 0) return tokens;

    // 2. Fallback: Fetch from Firestore users/{uid}
    if (firebaseAdmin.isFirebaseEnabled()) {
        try {
            const db = firebaseAdmin.getFirestore();
            if (db) {
                const doc = await db.collection('users').doc(firebaseUid).get();
                if (doc.exists && doc.data().fcmTokens) {
                    tokens = doc.data().fcmTokens.filter(t => t.active !== false).map(t => t.token);
                }
            }
        } catch (err) {
            console.error('[BE2][CART_RECOVERY] Firestore token fetch error:', err.message);
        }
    }

    return tokens;
}

/**
 * Core business logic: scan abandoned carts in Firestore and trigger recovery notifications
 */
async function runCartRecovery() {
    console.log('[BE2][CART_RECOVERY] Starting Cart Recovery scan...');
    const stats = { scanned: 0, sent: 0, skipped: 0, errors: 0 };

    if (!firebaseAdmin.isFirebaseEnabled()) {
        console.warn('[BE2][CART_RECOVERY] Firebase is disabled. Cart Recovery cancelled.');
        return stats;
    }

    try {
        const db = firebaseAdmin.getFirestore();
        if (!db) return stats;

        // Query Firestore carts that are active
        const cartsSnapshot = await db.collection('carts')
            .where('status', '==', 'active')
            .get();

        stats.scanned = cartsSnapshot.size;
        console.log(`[BE2][CART_RECOVERY] Scanned ${cartsSnapshot.size} active carts.`);

        const now = new Date();
        const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        for (const doc of cartsSnapshot.docs) {
            const cartData = doc.data();
            const firebaseUid = doc.id;

            // Basic validation
            if (!cartData.items || cartData.items.length === 0) {
                stats.skipped++;
                continue;
            }

            // Convert lastUpdatedAt timestamp
            let lastUpdatedAt = null;
            if (cartData.lastUpdatedAt) {
                lastUpdatedAt = cartData.lastUpdatedAt.toDate ? cartData.lastUpdatedAt.toDate() : new Date(cartData.lastUpdatedAt);
            }

            if (!lastUpdatedAt) {
                stats.skipped++;
                continue;
            }

            // Check if cart hasn't been updated for at least 24 hours
            if (lastUpdatedAt > oneDayAgo) {
                stats.skipped++;
                continue;
            }

            // Recovery limits
            const recoveryNotified = cartData.recoveryNotified || 0;
            if (recoveryNotified >= 2) {
                stats.skipped++;
                continue;
            }

            // Determine if we should notify
            let shouldNotify = false;
            let lastRecoveryNotifiedAt = null;

            if (cartData.lastRecoveryNotifiedAt) {
                lastRecoveryNotifiedAt = cartData.lastRecoveryNotifiedAt.toDate ? cartData.lastRecoveryNotifiedAt.toDate() : new Date(cartData.lastRecoveryNotifiedAt);
            }

            if (recoveryNotified === 0) {
                shouldNotify = true; // First notification: 24h of inactivity
            } else if (recoveryNotified === 1 && lastRecoveryNotifiedAt) {
                // Second notification: 48h total inactivity (24h since last recovery notification)
                const oneDaySinceLastNotif = new Date(now.getTime() - 24 * 60 * 60 * 1000);
                if (lastRecoveryNotifiedAt <= oneDaySinceLastNotif) {
                    shouldNotify = true;
                }
            }

            if (!shouldNotify) {
                stats.skipped++;
                continue;
            }

            // Trigger notification
            try {
                const tokens = await getTokensForFirebaseUid(firebaseUid);
                if (tokens.length === 0) {
                    console.log(`[BE2][CART_RECOVERY] Skipping cart recovery for UID: ${firebaseUid} (no FCM tokens)`);
                    stats.skipped++;
                    continue;
                }

                const firstItemName = cartData.items[0].name || "sản phẩm";
                
                // Build payload matching Phase 2 requirements
                const payload = {
                    title: "Bạn quên sản phẩm trong giỏ hàng",
                    body: `Bạn còn ${firstItemName} trong giỏ!`,
                    data: {
                        screen: "CART",
                        type: "CART_RECOVERY"
                    }
                };

                const pushResult = await firebaseAdmin.sendPushToTokens(tokens, payload);
                
                if (pushResult && pushResult.successCount > 0) {
                    // Update Firestore cart state
                    const admin = require('firebase-admin');
                    await db.collection('carts').doc(firebaseUid).update({
                        recoveryNotified: recoveryNotified + 1,
                        lastRecoveryNotifiedAt: admin.firestore.FieldValue.serverTimestamp()
                    });

                    stats.sent++;
                    console.log(`[BE2][CART_RECOVERY] Sent cart recovery FCM to UID: ${firebaseUid}`);
                } else {
                    stats.errors++;
                    console.warn(`[BE2][CART_RECOVERY] Failed to deliver FCM to tokens for UID: ${firebaseUid}`);
                }

            } catch (notifErr) {
                stats.errors++;
                console.error(`[BE2][CART_RECOVERY] Error sending recovery push to ${firebaseUid}:`, notifErr.message);
            }
        }

    } catch (err) {
        stats.errors++;
        console.error('[BE2][CART_RECOVERY] Cart recovery scan error:', err.message);
    }

    console.log(`[BE2][CART_RECOVERY] Completed scan. Stats: Sent=${stats.sent}, Skipped=${stats.skipped}, Errors=${stats.errors}`);
    return stats;
}

module.exports = {
    getTokensForFirebaseUid,
    runCartRecovery
};
