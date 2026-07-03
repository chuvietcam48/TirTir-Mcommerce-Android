const { getFirestore, isFirebaseEnabled } = require('../services/firebaseAdmin.service');
const User = require('../models/user.model');
const Product = require('../models/product.model');
const Coupon = require('../models/coupon.model');

// POST /api/v1/routines/save
exports.saveRoutine = async (req, res) => {
    try {
        const { steps, isPublic, name, description } = req.body;
        const userId = req.user?.id;

        if (!steps || !Array.isArray(steps) || steps.length === 0) {
            return res.status(400).json({ success: false, message: 'Steps are required' });
        }

        let firebaseUid = null;
        let userName = 'Anonymous';
        if (userId) {
            const user = await User.findById(userId);
            if (user) {
                firebaseUid = user.firebaseUid;
                userName = user.name || userName;
            }
        }

        if (!firebaseUid) {
            return res.status(400).json({ success: false, message: 'Firebase UID is required to save routine.' });
        }

        if (!isFirebaseEnabled()) {
            return res.status(503).json({ success: false, message: 'Firebase is not enabled.' });
        }

        const db = getFirestore();
        if (!db) {
            return res.status(500).json({ success: false, message: 'Firestore is unavailable.' });
        }

        const routineData = {
            userId: String(userId || ''),
            firebaseUid,
            userName,
            name: name || 'My Custom Routine',
            description: description || '',
            steps: steps,
            likes: 0,
            isPublic: !!isPublic,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
        };

        const admin = require('firebase-admin');

        // Save to user's active routine
        const userRef = db.collection('users').doc(firebaseUid);
        await userRef.set({
            activeRoutine: routineData,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });

        let grantedVoucher = null;

        // Sharing reward logic (isPublic and >= 4 steps)
        if (isPublic && steps.length >= 4) {
            // Generate voucher code TIRTIR-SHARE-XXXX
            const code = 'TIRTIR-SHARE-' + Math.random().toString(36).substring(2, 6).toUpperCase();
            
            // Save to Firestore under users/{uid}/vouchers
            const voucherData = {
                code,
                voucherCode: code,
                discountPct: 5,
                type: 'percentage',
                description: 'Routine Sharing Reward',
                createdAt: new Date().toISOString(),
                expiryDays: 7,
                expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
                active: true
            };

            await db.collection('users').doc(firebaseUid).collection('vouchers').add(voucherData);

            // Save to MongoDB Coupon collection for checkout validation
            await Coupon.create({
                code,
                discountType: 'percentage',
                discountValue: 5,
                minOrderValue: 0,
                usageLimit: 1,
                usedCount: 0,
                validFrom: new Date(),
                validTo: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
                active: true
            });

            grantedVoucher = voucherData;

            // Also save routine to global public_routines collection
            await db.collection('public_routines').add(routineData);
            console.log(`[BE2][ROUTINE] Granted 5% voucher ${code} to ${firebaseUid} for sharing public routine`);
        }

        return res.status(200).json({
            success: true,
            message: 'Routine saved successfully',
            data: routineData,
            voucher: grantedVoucher
        });

    } catch (error) {
        console.error('[BE2][ROUTINE] Save routine error:', error);
        return res.status(500).json({ success: false, message: error.message });
    }
};

// GET /api/v1/routines/community
exports.getCommunityRoutines = async (req, res) => {
    try {
        if (!isFirebaseEnabled()) {
            return res.status(503).json({ success: false, message: 'Firebase is not enabled.' });
        }

        const db = getFirestore();
        if (!db) {
            return res.status(500).json({ success: false, message: 'Firestore is unavailable.' });
        }

        const routinesSnapshot = await db.collection('public_routines').limit(30).get();
        const routines = [];
        routinesSnapshot.forEach(doc => {
            routines.push({ id: doc.id, ...doc.data() });
        });

        return res.status(200).json({
            success: true,
            data: routines
        });

    } catch (error) {
        console.error('[BE2][ROUTINE] Get community routines error:', error);
        return res.status(500).json({ success: false, message: error.message });
    }
};

// POST /api/v1/routines/:id/like
exports.likeRoutine = async (req, res) => {
    try {
        const { id } = req.params;
        if (!isFirebaseEnabled()) {
            return res.status(503).json({ success: false, message: 'Firebase is not enabled.' });
        }

        const db = getFirestore();
        if (!db) {
            return res.status(500).json({ success: false, message: 'Firestore is unavailable.' });
        }

        const routineRef = db.collection('public_routines').doc(id);
        const doc = await routineRef.get();
        if (!doc.exists) {
            return res.status(404).json({ success: false, message: 'Routine not found' });
        }

        const admin = require('firebase-admin');
        await routineRef.update({
            likes: admin.firestore.FieldValue.increment(1),
            updatedAt: new Date().toISOString()
        });

        return res.status(200).json({
            success: true,
            message: 'Routine liked successfully'
        });

    } catch (error) {
        console.error('[BE2][ROUTINE] Like routine error:', error);
        return res.status(500).json({ success: false, message: error.message });
    }
};

// POST /api/v1/routines/:id/apply
exports.applyRoutine = async (req, res) => {
    try {
        const { id } = req.params;
        const userId = req.user?.id;

        if (!userId) {
            return res.status(401).json({ success: false, message: 'Unauthorized' });
        }

        const user = await User.findById(userId);
        if (!user || !user.firebaseUid) {
            return res.status(400).json({ success: false, message: 'User Firebase UID not found' });
        }

        if (!isFirebaseEnabled()) {
            return res.status(503).json({ success: false, message: 'Firebase is not enabled.' });
        }

        const db = getFirestore();
        if (!db) {
            return res.status(500).json({ success: false, message: 'Firestore is unavailable.' });
        }

        const routineRef = db.collection('public_routines').doc(id);
        const doc = await routineRef.get();
        if (!doc.exists) {
            return res.status(404).json({ success: false, message: 'Routine not found' });
        }

        const routineData = doc.data();
        const admin = require('firebase-admin');
        
        // Save to user's active routine
        const userRef = db.collection('users').doc(user.firebaseUid);
        await userRef.set({
            activeRoutine: {
                ...routineData,
                userId: String(userId),
                firebaseUid: user.firebaseUid,
                userName: user.name || 'Anonymous',
                isPublic: false,
                likes: 0,
                appliedFrom: id,
                appliedAt: new Date().toISOString()
            },
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });

        return res.status(200).json({
            success: true,
            message: 'Routine applied successfully'
        });

    } catch (error) {
        console.error('[BE2][ROUTINE] Apply routine error:', error);
        return res.status(500).json({ success: false, message: error.message });
    }
};

// GET /api/v1/routine/suggest
exports.suggestRoutine = async (req, res) => {
    try {
        const { missingStep } = req.query;
        if (!missingStep) {
            return res.status(400).json({ success: false, message: 'missingStep query parameter is required' });
        }

        // Clean query to avoid regex vulnerabilities
        const cleanStep = String(missingStep).replace(/[^a-zA-Z0-9\s-]/g, '').trim();

        let queryCategory = cleanStep;
        if (cleanStep.toLowerCase() === 'spf') {
            queryCategory = 'Sunscreen';
        }

        const isMakeupStep = /cushion|foundation|concealer|makeup|bb|cc|powder|lip|eye/i.test(queryCategory);

        const filterQuery = {
            Stock_Quantity: { $gt: 0 },
            $or: [
                { Category: { $regex: new RegExp(queryCategory, 'i') } },
                { Name: { $regex: new RegExp(queryCategory, 'i') } }
            ]
        };

        if (!isMakeupStep) {
            filterQuery.$and = [
                { Category: { $not: /makeup|cushion/i } },
                { Name: { $not: /cushion|foundation|concealer|bb cream|cc cream/i } }
            ];
        }

        let products = await Product.find(filterQuery).limit(5).select('Name Category Price Thumbnail_Images slug Product_ID');

        // Fallback: if no products found, fetch any from Skincare category
        if (products.length === 0) {
            products = await Product.find({
                Category: 'Skincare',
                Stock_Quantity: { $gt: 0 }
            }).limit(5).select('Name Category Price Thumbnail_Images slug Product_ID');
        }

        return res.status(200).json({
            success: true,
            data: {
                suggestedStep: cleanStep,
                products: products
            }
        });

    } catch (error) {
        console.error('[BE2][ROUTINE] Suggest routine error:', error);
        return res.status(500).json({ success: false, message: error.message });
    }
};
