const firebaseAdmin = require('../services/firebaseAdmin.service');
const { findFirebaseUidByMongoUserId } = require('../services/firestoreUser.service');
const { getNextTierDetails, determineTier } = require('../services/loyalty.service');
const User = require('../models/user.model');
const ScanHistory = require('../models/scan_history.model');
const Voucher = require('../models/voucher.model');
const crypto = require('crypto');

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

/**
 * @desc    Scan barcode (O2O) to earn points (Max 1 time/month)
 * @route   POST /api/v1/loyalty/scan
 * @access  Private
 */
exports.scanBarcode = async (req, res, next) => {
    try {
        const userId = req.user ? (req.user.id || req.user._id) : req.body.userId;
        const { barcodeValue } = req.body;

        if (!userId) {
            return res.status(400).json({ success: false, message: 'userId is required' });
        }

        if (!barcodeValue || typeof barcodeValue !== 'string' || !barcodeValue.trim().startsWith('TIRTIR-')) {
            return res.status(400).json({ success: false, message: 'Mã vạch không hợp lệ. Mã phải bắt đầu bằng TIRTIR-' });
        }

        const trimmedBarcode = barcodeValue.trim();
        const now = new Date();
        const monthKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
        const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
        const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);

        // Check if barcode has already been scanned globally or by this user
        const duplicateBarcode = await ScanHistory.findOne({ barcodeValue: trimmedBarcode });
        if (duplicateBarcode) {
            return res.status(409).json({ success: false, message: 'Mã vạch này đã được sử dụng.' });
        }

        // Check if user already scanned this month
        const existingScan = await ScanHistory.findOne({
            $or: [
                { userId: String(userId), monthKey },
                { userId: userId, createdAt: { $gte: startOfMonth, $lte: endOfMonth } }
            ]
        });

        if (existingScan) {
            return res.status(409).json({ success: false, message: 'Bạn đã quét mã trong tháng này. Hãy quay lại vào tháng sau nhé!' });
        }

        // Save scan record
        await ScanHistory.create({
            userId: String(userId),
            barcodeValue: trimmedBarcode,
            monthKey,
            pointsEarned: 50
        });

        // Add 50 points to user
        const user = await User.findById(userId);
        if (!user) {
            return res.status(404).json({ success: false, message: 'Người dùng không tồn tại' });
        }

        const previousPoints = user.loyaltyPoints || 0;
        const previousTier = user.loyaltyTier || determineTier(previousPoints);
        const newPoints = previousPoints + 50;
        const newTier = determineTier(newPoints);
        const tierChanged = newTier !== previousTier;

        user.loyaltyPoints = newPoints;
        user.loyaltyTier = newTier;
        await user.save({ validateBeforeSave: false });

        // Sync with Firestore & store audit loyalty history
        const firebaseUid = await findFirebaseUidByMongoUserId(userId);
        if (firebaseUid && firebaseAdmin.isFirebaseEnabled()) {
            const db = firebaseAdmin.getFirestore();
            if (db) {
                const userRef = db.collection('users').doc(firebaseUid);
                await userRef.set({
                    loyaltyPoints: newPoints,
                    loyaltyTier: newTier,
                    updatedAt: firebaseAdmin.getFirestore().FieldValue.serverTimestamp()
                }, { merge: true });

                await userRef.collection('loyalty_history').add({
                    userId: String(userId),
                    type: 'BARCODE_EARN',
                    source: 'SCAN_BARCODE',
                    barcodeValue: trimmedBarcode,
                    basePoints: 50,
                    multiplier: 1,
                    reason: 'BARCODE_SCAN',
                    finalPoints: 50,
                    points: 50,
                    previousPoints,
                    newPoints,
                    previousTier,
                    newTier,
                    tierChanged,
                    createdAt: firebaseAdmin.getFirestore().FieldValue.serverTimestamp()
                });
            }
        }

        // Push FCM notification if tier changed
        if (tierChanged) {
            firebaseAdmin.sendLoyaltyTierPush(userId, newTier).catch(err => {
                console.error('[BE2][LOYALTY] Failed to send tier push on barcode scan:', err.message);
            });
        }

        return res.status(200).json({
            success: true,
            message: 'Quét mã thành công! Bạn nhận được 50 điểm.',
            pointsEarned: 50,
            totalPoints: newPoints,
            tier: newTier
        });
    } catch (err) {
        next(err);
    }
};


/**
 * @desc    Get static voucher redemption levels
 * @route   GET /api/v1/loyalty/vouchers
 * @access  Public or Private
 */
exports.getVouchersList = async (req, res, next) => {
    try {
        const levels = [
            { id: 1, pointsRequired: 100, discountPct: 5, label: 'Giảm 5% (100 điểm)' },
            { id: 2, pointsRequired: 200, discountPct: 10, label: 'Giảm 10% (200 điểm)' },
            { id: 3, pointsRequired: 500, discountPct: 25, label: 'Giảm 25% (500 điểm)' }
        ];
        res.status(200).json({ success: true, data: levels });
    } catch (err) {
        next(err);
    }
};

/**
 * @desc    Redeem loyalty points for a discount voucher
 * @route   POST /api/v1/loyalty/redeem
 * @access  Private
 */
exports.redeemPoints = async (req, res, next) => {
    try {
        const userId = req.user.id;
        const { ptsRequired } = req.body;

        if (!ptsRequired) {
            return res.status(400).json({ success: false, message: 'Thiếu thông tin số điểm cần đổi' });
        }

        const levels = {
            100: 5,
            200: 10,
            500: 25
        };

        if (!levels[ptsRequired]) {
            return res.status(400).json({ success: false, message: 'Mức đổi điểm không hợp lệ' });
        }

        const discountPct = levels[ptsRequired];
        const user = await User.findById(userId);

        if (!user || (user.loyaltyPoints || 0) < ptsRequired) {
            return res.status(400).json({ success: false, message: 'Không đủ điểm để đổi voucher này' });
        }

        // Deduct points
        user.loyaltyPoints -= ptsRequired;
        user.loyaltyTier = determineTier(user.loyaltyPoints);
        await user.save({ validateBeforeSave: false });

        // Generate voucher code
        const code = 'TIRTIR-' + crypto.randomBytes(3).toString('hex').toUpperCase();
        const validTo = new Date();
        validTo.setDate(validTo.getDate() + 30); // 30 days validity

        const voucher = await Voucher.create({
            code,
            userId,
            discountPct,
            validTo,
            source: 'LoyaltyRedeem'
        });

        // Sync with Firestore
        const firebaseUid = await findFirebaseUidByMongoUserId(userId);
        if (firebaseUid && firebaseAdmin.isFirebaseEnabled()) {
            const db = firebaseAdmin.getFirestore();
            if (db) {
                const userRef = db.collection('users').doc(firebaseUid);
                
                await userRef.set({
                    loyaltyPoints: user.loyaltyPoints,
                    loyaltyTier: user.loyaltyTier
                }, { merge: true });

                // Add to firestore vouchers subcollection
                await userRef.collection('vouchers').doc(code).set({
                    code,
                    discountPct,
                    validTo: firebaseAdmin.getFirestore().Timestamp.fromDate(validTo),
                    isUsed: false,
                    createdAt: firebaseAdmin.getFirestore().FieldValue.serverTimestamp()
                });

                // Add to loyalty history
                await userRef.collection('loyalty_history').add({
                    source: 'REDEEM_VOUCHER',
                    pointsDeducted: ptsRequired,
                    finalPoints: -ptsRequired,
                    newTier: user.loyaltyTier,
                    createdAt: firebaseAdmin.getFirestore().FieldValue.serverTimestamp()
                });
            }
        }

        res.status(200).json({
            success: true,
            data: {
                voucherCode: code,
                discountPct
            }
        });
    } catch (err) {
        next(err);
    }
};

/**
 * @desc    Get current user's unused vouchers wallet
 * @route   GET /api/v1/loyalty/wallet
 * @access  Private
 */
exports.getWallet = async (req, res, next) => {
    try {
        const userId = req.user.id;
        const vouchers = await Voucher.find({
            userId,
            isUsed: false,
            validTo: { $gte: new Date() }
        }).sort({ createdAt: -1 });

        res.status(200).json({ success: true, count: vouchers.length, data: vouchers });
    } catch (err) {
        next(err);
    }
};
