const firebaseAdmin = require('./firebaseAdmin.service');

/**
 * Helper to get YYYY-MM-DD string from date
 */
function getDateKey(date) {
    const d = new Date(date || Date.now());
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

/**
 * Update daily analytics when a new order is created
 */
async function updateDailyAnalyticsOnOrderCreated(order) {
    if (!order) return;
    
    console.log(`[BE2][ANALYTICS] Updating daily stats for order creation: ID=${order._id}, Amount=${order.totalAmount}`);
    if (!firebaseAdmin.isFirebaseEnabled()) {
        console.warn(`[BE2][ANALYTICS] Firebase is disabled. Skipping Firestore analytics update.`);
        return;
    }

    try {
        const db = firebaseAdmin.getFirestore();
        if (!db) return;

        const dateKey = getDateKey(order.createdAt);
        const docRef = db.collection('analytics').doc(dateKey);

        const admin = require('firebase-admin');
        await docRef.set({
            totalOrders: admin.firestore.FieldValue.increment(1),
            revenue: admin.firestore.FieldValue.increment(order.totalAmount || 0),
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });

        console.log(`[BE2][ANALYTICS] Firestore daily stats updated for date: ${dateKey}`);
    } catch (err) {
        console.warn(`[BE2][ANALYTICS] Failed to update daily stats:`, err.message);
    }
}

/**
 * Adjust daily analytics if order is Cancelled or un-Cancelled
 */
async function updateDailyAnalyticsOnOrderStatusChanged(order, oldStatus, newStatus) {
    if (!order || !oldStatus || !newStatus || oldStatus === newStatus) return;

    const isOldCancelled = oldStatus.toLowerCase() === 'cancelled';
    const isNewCancelled = newStatus.toLowerCase() === 'cancelled';

    // No cancel state transition, no changes to revenue/count
    if (isOldCancelled === isNewCancelled) return;

    console.log(`[BE2][ANALYTICS] Adjusting daily stats for order status transition: ID=${order._id}, ${oldStatus} -> ${newStatus}`);
    if (!firebaseAdmin.isFirebaseEnabled()) return;

    try {
        const db = firebaseAdmin.getFirestore();
        if (!db) return;

        const dateKey = getDateKey(order.createdAt);
        const docRef = db.collection('analytics').doc(dateKey);
        
        const admin = require('firebase-admin');
        let orderIncrement = 0;
        let revenueIncrement = 0;

        if (isNewCancelled) {
            // Non-cancelled -> Cancelled: Subtract from revenue and total orders
            orderIncrement = -1;
            revenueIncrement = -(order.totalAmount || 0);
        } else if (isOldCancelled) {
            // Cancelled -> Non-cancelled: Re-add to revenue and total orders
            orderIncrement = 1;
            revenueIncrement = order.totalAmount || 0;
        }

        if (orderIncrement !== 0 || revenueIncrement !== 0) {
            await docRef.set({
                totalOrders: admin.firestore.FieldValue.increment(orderIncrement),
                revenue: admin.firestore.FieldValue.increment(revenueIncrement),
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            }, { merge: true });
            console.log(`[BE2][ANALYTICS] Adjusted daily stats in Firestore: Date=${dateKey}, Orders=${orderIncrement}, Revenue=${revenueIncrement}`);
        }
    } catch (err) {
        console.warn(`[BE2][ANALYTICS] Failed to adjust daily stats:`, err.message);
    }
}

module.exports = {
    getDateKey,
    updateDailyAnalyticsOnOrderCreated,
    updateDailyAnalyticsOnOrderStatusChanged
};
