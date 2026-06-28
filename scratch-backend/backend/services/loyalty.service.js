const mongoose = require('mongoose');
const User = require('../models/user.model');
const Order = require('../models/order.model');
const firebaseAdmin = require('./firebaseAdmin.service');
const { findFirebaseUidByMongoUserId } = require('./firestoreUser.service');

/**
 * Determine loyalty tier based on total points
 */
function determineTier(points) {
    const silverThreshold = process.env.LOYALTY_SILVER_THRESHOLD ? parseInt(process.env.LOYALTY_SILVER_THRESHOLD, 10) : 100;
    const goldThreshold = process.env.LOYALTY_GOLD_THRESHOLD ? parseInt(process.env.LOYALTY_GOLD_THRESHOLD, 10) : 500;
    const platinumThreshold = process.env.LOYALTY_PLATINUM_THRESHOLD ? parseInt(process.env.LOYALTY_PLATINUM_THRESHOLD, 10) : 2000;

    if (points >= platinumThreshold) return 'Platinum';
    if (points >= goldThreshold) return 'Gold';
    return 'Silver'; // Default tier
}

/**
 * Get next tier details
 */
function getNextTierDetails(points) {
    const goldThreshold = process.env.LOYALTY_GOLD_THRESHOLD ? parseInt(process.env.LOYALTY_GOLD_THRESHOLD, 10) : 500;
    const platinumThreshold = process.env.LOYALTY_PLATINUM_THRESHOLD ? parseInt(process.env.LOYALTY_PLATINUM_THRESHOLD, 10) : 2000;

    if (points < goldThreshold) {
        return { nextTier: 'Gold', pointsToNextTier: goldThreshold - points };
    } else if (points < platinumThreshold) {
        return { nextTier: 'Platinum', pointsToNextTier: platinumThreshold - points };
    }
    return { nextTier: 'Max', pointsToNextTier: 0 };
}

/**
 * Add loyalty points to a user based on order amount
 * Supports positional (userId, orderTotal, orderId) and options object (userId, orderTotal, { orderId })
 */
async function addPoints(userId, orderTotal, optionsOrOrderId = {}) {
    let orderId = null;
    if (typeof optionsOrOrderId === 'object' && optionsOrOrderId !== null) {
        orderId = optionsOrOrderId.orderId || optionsOrOrderId._id || optionsOrOrderId.id;
    } else if (typeof optionsOrOrderId === 'string' || optionsOrOrderId instanceof mongoose.Types.ObjectId) {
        orderId = String(optionsOrOrderId);
    }

    if (!userId || orderTotal === undefined || orderTotal === null || !orderId) {
        console.warn('[BE2][LOYALTY] Missing required parameters in addPoints. Skipping.');
        return { skipped: true, reason: 'Missing parameters' };
    }

    const numOrderTotal = Number(orderTotal);
    if (isNaN(numOrderTotal) || numOrderTotal <= 0) {
        return { skipped: true, reason: 'Invalid orderTotal' };
    }

    console.log(`[BE2][LOYALTY] Attempting to add points for user ${userId}, order ${orderId}, amount ${numOrderTotal}`);

    // 1. Fetch Mongo User
    const user = await User.findById(userId);
    if (!user) {
        console.warn(`[BE2][LOYALTY] User not found: ${userId}`);
        return { skipped: true, reason: 'User not found' };
    }

    // 2. Determine base points: floor(orderTotal / 1000)
    const basePoints = Math.floor(numOrderTotal / 1000);
    if (basePoints <= 0) {
        return {
            skipped: true,
            reason: 'Order total too low for points',
            basePoints: 0,
            finalPoints: 0
        };
    }

    // 3. Determine Multipliers & Non-stacking highest multiplier rule
    const pastOrdersCount = await Order.countDocuments({
        user: userId,
        _id: { $ne: orderId },
        status: { $in: ['Processing', 'Shipped', 'Delivered'] }
    });
    const isFirstOrder = pastOrdersCount === 0 || (user.totalOrders || 0) === 0;

    let isBirthdayMonth = false;
    if (user.birthDate || user.birthday) {
        const bDate = user.birthDate || user.birthday;
        try {
            const birthMonth = new Date(bDate).getMonth();
            const currentMonth = new Date().getMonth();
            isBirthdayMonth = birthMonth === currentMonth;
        } catch (e) {}
    }

    const firstOrderMult = process.env.LOYALTY_FIRST_ORDER_MULTIPLIER ? parseInt(process.env.LOYALTY_FIRST_ORDER_MULTIPLIER, 10) : 2;
    const birthdayMult = process.env.LOYALTY_BIRTHDAY_MULTIPLIER ? parseInt(process.env.LOYALTY_BIRTHDAY_MULTIPLIER, 10) : 3;

    let multiplier = 1;
    let reason = 'STANDARD';
    const combinedReasons = [];

    if (isFirstOrder) combinedReasons.push('FIRST_ORDER');
    if (isBirthdayMonth) combinedReasons.push('BIRTHDAY_MONTH');

    // Non-stacking rule: Highest multiplier only
    if (isFirstOrder && isBirthdayMonth) {
        multiplier = birthdayMult; // 3
        reason = 'BIRTHDAY_MONTH';
    } else if (isBirthdayMonth) {
        multiplier = birthdayMult; // 3
        reason = 'BIRTHDAY_MONTH';
    } else if (isFirstOrder) {
        multiplier = firstOrderMult; // 2
        reason = 'FIRST_ORDER';
    } else {
        multiplier = 1;
        reason = 'STANDARD';
    }

    const finalPoints = basePoints * multiplier;
    console.log(`[BE2][LOYALTY] Points calculation: Base=${basePoints}, Multiplier=${multiplier} (${reason}), Final=${finalPoints}`);

    // 4. Update user points & tier in MongoDB & Firestore
    let previousPoints = user.loyaltyPoints || 0;
    let previousTier = user.loyaltyTier || determineTier(previousPoints);
    let newPoints = previousPoints + finalPoints;
    let newTier = determineTier(newPoints);
    let tierChanged = newTier !== previousTier;

    // Firebase UID check
    const firebaseUid = await findFirebaseUidByMongoUserId(userId);
    const firebaseEnabled = firebaseAdmin.isFirebaseEnabled();
    const db = firebaseEnabled ? firebaseAdmin.getFirestore() : null;

    if (firebaseUid && db) {
        try {
            const userRef = db.collection('users').doc(firebaseUid);
            const historyRef = userRef.collection('loyalty_history').doc(String(orderId));

            // Idempotency Check
            const historyDoc = await historyRef.get();
            if (historyDoc.exists) {
                console.log(`[BE2][LOYALTY] Points for order ${orderId} already processed in Firestore. Skipping.`);
                return { skipped: true, reason: 'Idempotency match' };
            }

            const admin = require('firebase-admin');
            await db.runTransaction(async (transaction) => {
                const userDoc = await transaction.get(userRef);
                let currentPoints = 0;
                let currentOrders = 0;

                if (userDoc.exists) {
                    const data = userDoc.data();
                    currentPoints = data.loyaltyPoints || 0;
                    previousTier = data.loyaltyTier || determineTier(currentPoints);
                    currentOrders = data.totalOrders || 0;
                }

                previousPoints = currentPoints;
                newPoints = currentPoints + finalPoints;
                newTier = determineTier(newPoints);
                tierChanged = newTier !== previousTier;

                transaction.set(userRef, {
                    loyaltyPoints: newPoints,
                    loyaltyTier: newTier,
                    totalOrders: currentOrders + 1,
                    lastOrderAt: admin.firestore.FieldValue.serverTimestamp(),
                    updatedAt: admin.firestore.FieldValue.serverTimestamp()
                }, { merge: true });

                transaction.set(historyRef, {
                    userId: String(userId),
                    orderId: String(orderId),
                    type: 'EARN',
                    source: 'ORDER',
                    basePoints,
                    multiplier,
                    reason,
                    combinedReasons,
                    finalPoints,
                    points: finalPoints,
                    previousPoints,
                    newPoints,
                    previousTier,
                    newTier,
                    tierChanged,
                    createdAt: admin.firestore.FieldValue.serverTimestamp()
                });
            });
        } catch (fsErr) {
            console.error('[BE2][LOYALTY] Firestore transaction error:', fsErr.message);
        }
    }

    // Update MongoDB User document
    user.loyaltyPoints = newPoints;
    user.loyaltyTier = newTier;
    user.totalOrders = (user.totalOrders || 0) + 1;
    await user.save({ validateBeforeSave: false });

    console.log(`[BE2][LOYALTY] Successfully added ${finalPoints} points to user ${userId}. New total: ${newPoints}. Tier: ${newTier}`);

    // 5. Trigger Tier Up FCM Push Notification
    if (tierChanged) {
        console.log(`[BE2][LOYALTY] Tier upgraded from ${previousTier} to ${newTier} for user ${userId}. Triggering FCM push.`);
        firebaseAdmin.sendLoyaltyTierPush(userId, newTier).catch(err => {
            console.error('[BE2][LOYALTY] Failed to send loyalty tier push:', err.message);
        });
    }

    return {
        success: true,
        userId: String(userId),
        orderId: String(orderId),
        basePoints,
        multiplier,
        reason,
        finalPoints,
        previousPoints,
        newPoints,
        previousTier,
        newTier,
        tierChanged
    };
}

module.exports = {
    determineTier,
    getNextTierDetails,
    addPoints
};

