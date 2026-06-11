const firebaseAdmin = require('../services/firebaseAdmin.service');
const { findFirebaseUidByMongoUserId } = require('../services/firestoreUser.service');
const { getNextTierDetails, determineTier } = require('../services/loyalty.service');
const User = require('../models/user.model');

/**
 * @desc    Get loyalty summary for current user
 * @route   GET /api/v1/loyalty/me
 * @access  Private
 */
exports.getLoyaltyDetails = async (req, res, next) => {
    try {
        const userId = req.user.id;
        
        let loyaltyPoints = 0;
        let loyaltyTier = 'Bronze';
        let history = [];

        // 1. Map MongoDB userId to Firebase UID
        const firebaseUid = await findFirebaseUidByMongoUserId(userId);
        
        if (firebaseUid && firebaseAdmin.isFirebaseEnabled()) {
            const db = firebaseAdmin.getFirestore();
            if (db) {
                // Fetch profile stats from Firestore
                const userDoc = await db.collection('users').doc(firebaseUid).get();
                if (userDoc.exists) {
                    const data = userDoc.data();
                    loyaltyPoints = data.loyaltyPoints || 0;
                    loyaltyTier = data.loyaltyTier || determineTier(loyaltyPoints);
                }

                // Fetch recent history
                const historySnapshot = await db.collection('users').doc(firebaseUid)
                    .collection('loyalty_history')
                    .orderBy('createdAt', 'desc')
                    .limit(20)
                    .get();

                historySnapshot.forEach(doc => {
                    const hData = doc.data();
                    if (hData.createdAt && hData.createdAt.toDate) {
                        hData.createdAt = hData.createdAt.toDate();
                    }
                    history.push({ id: doc.id, ...hData });
                });
            }
        } else {
            // Fallback: Read from MongoDB User document
            const user = await User.findById(userId).select('loyaltyPoints loyaltyTier');
            if (user) {
                loyaltyPoints = user.loyaltyPoints || 0;
                loyaltyTier = user.loyaltyTier || 'Bronze';
            }
        }

        const nextDetails = getNextTierDetails(loyaltyPoints);

        res.status(200).json({
            success: true,
            data: {
                loyaltyPoints,
                loyaltyTier,
                ...nextDetails,
                history
            }
        });
    } catch (err) {
        next(err);
    }
};

/**
 * @desc    Get full loyalty point history for current user
 * @route   GET /api/v1/loyalty/history
 * @access  Private
 */
exports.getLoyaltyHistory = async (req, res, next) => {
    try {
        const userId = req.user.id;
        let history = [];

        const firebaseUid = await findFirebaseUidByMongoUserId(userId);
        if (firebaseUid && firebaseAdmin.isFirebaseEnabled()) {
            const db = firebaseAdmin.getFirestore();
            if (db) {
                const historySnapshot = await db.collection('users').doc(firebaseUid)
                    .collection('loyalty_history')
                    .orderBy('createdAt', 'desc')
                    .get();

                historySnapshot.forEach(doc => {
                    const hData = doc.data();
                    if (hData.createdAt && hData.createdAt.toDate) {
                        hData.createdAt = hData.createdAt.toDate();
                    }
                    history.push({ id: doc.id, ...hData });
                });
            }
        }

        res.status(200).json({
            success: true,
            count: history.length,
            data: history
        });
    } catch (err) {
        next(err);
    }
};
