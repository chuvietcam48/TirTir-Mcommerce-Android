const User = require('../models/user.model');
const Order = require('../models/order.model');
const firebaseAdmin = require('./firebaseAdmin.service');
const { findFirebaseUidByMongoUserId } = require('./firestoreUser.service');

/**
 * Determine loyalty tier based on total points
 */
function determineTier(points) {
    if (points >= 2000) return 'Platinum';
    if (points >= 500) return 'Gold';
    if (points >= 100) return 'Silver';
    return 'Bronze'; // Default tier below 100 points
}

/**
 * Get next tier details
 */
function getNextTierDetails(points) {
    if (points < 100) {
        return { nextTier: 'Silver', pointsToNextTier: 100 - points };
    } else if (points < 500) {
        return { nextTier: 'Gold', pointsToNextTier: 500 - points };
    } else if (points < 2000) {
        return { nextTier: 'Platinum', pointsToNextTier: 2000 - points };
    }
    return { nextTier: 'Max', pointsToNextTier: 0 };
}

/**
 * Add loyalty points to a user based on order amount
 */
async function addPoints(userId, orderTotal, options = {}) {
    const { orderId } = options;
    if (!userId || !orderTotal || !orderId) {
        console.warn('[BE2][LOYALTY] Missing parameters in addPoints. Skipping.');
        return { skipped: true, reason: 'Missing parameters' };
    }

    console.log(`[BE2][LOYALTY] Attempting to add points for user ${userId}, order ${orderId}`);

    // 1. Fetch Mongo User
    const user = await User.findById(userId);
    if (!user) {
        console.warn(`[BE2][LOYALTY] User not found: ${userId}`);
        return { skipped: true, reason: 'User not found' };
    }

    // 2. Find Firebase UID
    const firebaseUid = await findFirebaseUidByMongoUserId(userId);
    if (!firebaseUid) {
        console.warn(`[BE2][LOYALTY] No Firebase UID mapped for user ${userId}. Cannot add loyalty points.`);
        return { skipped: true, reason: 'Firebase UID not found' };
    }

    if (!firebaseAdmin.isFirebaseEnabled()) {
        console.warn(`[BE2][LOYALTY] Firebase is disabled. Cannot update loyalty points.`);
        return { skipped: true, reason: 'Firebase disabled' };
    }

    const db = firebaseAdmin.getFirestore();
    if (!db) return { skipped: true, reason: 'Firestore unavailable' };

    try {
        const userRef = db.collection('users').doc(firebaseUid);
        const historyRef = userRef.collection('loyalty_history').doc(String(orderId));

        // 3. Idempotency Check: Check if this order has already processed points
        const historyDoc = await historyRef.get();
        if (historyDoc.exists) {
            console.log(`[BE2][LOYALTY] Points for order ${orderId} already processed. Skipping.`);
            return { skipped: true, reason: 'Idempotency match' };
        }

        // 4. Calculate Multipliers
        // Condition A: First order (check past completed/confirmed orders excluding current order)
        const pastOrdersCount = await Order.countDocuments({
            user: userId,
            _id: { $ne: orderId },
            status: { $in: ['Processing', 'Shipped', 'Delivered'] }
        });
        const isFirstOrder = pastOrdersCount === 0;

        // Condition B: Birthday Month matches current month
        let isBirthdayMonth = false;
        if (user.birthDate) {
            const birthMonth = new Date(user.birthDate).getMonth();
            const currentMonth = new Date().getMonth();
            isBirthdayMonth = birthMonth === currentMonth;
        }

        let multiplier = 1;
        const reasons = [];

        if (isFirstOrder) {
            multiplier *= 2;
            reasons.push('FIRST_ORDER');
        }
        if (isBirthdayMonth) {
            multiplier *= 3;
            reasons.push('BIRTHDAY_MONTH');
        }

        const basePoints = Math.floor(orderTotal / 1000);
        const finalPoints = basePoints * multiplier;

        console.log(`[BE2][LOYALTY] Points calculation: Base=${basePoints}, Multiplier=${multiplier} (${reasons.join(', ') || 'NONE'}), Final=${finalPoints}`);

        // 5. Firestore Transaction to update user points and tier
        const admin = require('firebase-admin');
        let oldTier = 'Bronze';
        let newTier = 'Bronze';
        let tierChanged = false;
        let finalPointsTotal = 0;

        await db.runTransaction(async (transaction) => {
            const userDoc = await transaction.get(userRef);
            let currentPoints = 0;
            let currentOrders = 0;

            if (userDoc.exists) {
                const data = userDoc.data();
                currentPoints = data.loyaltyPoints || 0;
                oldTier = data.loyaltyTier || determineTier(currentPoints);
                currentOrders = data.totalOrders || 0;
            }

            finalPointsTotal = currentPoints + finalPoints;
            newTier = determineTier(finalPointsTotal);
            tierChanged = newTier !== oldTier;

            // Update user document
            transaction.set(userRef, {
                loyaltyPoints: finalPointsTotal,
                loyaltyTier: newTier,
                totalOrders: currentOrders + 1,
                lastOrderAt: admin.firestore.FieldValue.serverTimestamp(),
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            }, { merge: true });

            // Create loyalty history document
            transaction.set(historyRef, {
                orderId: String(orderId),
                source: "ORDER",
                basePoints,
                multiplier,
                reasons,
                finalPoints,
                oldTier,
                newTier,
                createdAt: admin.firestore.FieldValue.serverTimestamp()
            });
        });

        // 6. Update MongoDB User Document fields to sync cache
        user.loyaltyPoints = finalPointsTotal;
        user.loyaltyTier = newTier;
        user.totalOrders = (user.totalOrders || 0) + 1;
        await user.save({ validateBeforeSave: false });

        console.log(`[BE2][LOYALTY] Successfully added ${finalPoints} points to user ${userId}. New total: ${finalPointsTotal}. Tier: ${newTier}`);

        // 7. Tier Up Push Notification
        if (tierChanged) {
            console.log(`[BE2][LOYALTY] Tier upgraded from ${oldTier} to ${newTier} for user ${userId}. Sending FCM push.`);
            firebaseAdmin.sendLoyaltyTierPush(userId, newTier).catch(err => {
                console.error('[BE2][LOYALTY] Failed to send loyalty tier push:', err.message);
            });
        }

        return {
            success: true,
            userId,
            firebaseUid,
            basePoints,
            multiplier,
            reasons,
            finalPoints,
            oldTier,
            newTier,
            tierChanged
        };

    } catch (err) {
        console.error(`[BE2][LOYALTY] Error adding loyalty points:`, err.message);
        throw err;
    }
}

module.exports = {
    determineTier,
    getNextTierDetails,
    addPoints
};
