const firebaseAdmin = require('./firebaseAdmin.service');
const User = require('../models/user.model');

/**
 * Find Firebase UID associated with a MongoDB User ID.
 * Tries: 
 * 1. MongoDB user.firebaseUid
 * 2. MongoDB user.fcmTokens array item with firebaseUid
 * 3. Query Firestore 'users' collection where backendUserId == mongoUserId
 */
async function findFirebaseUidByMongoUserId(mongoUserId) {
    if (!mongoUserId) return null;

    try {
        // 1. Check in MongoDB User document first
        const user = await User.findById(mongoUserId).select('firebaseUid fcmTokens');
        if (user && user.firebaseUid) {
            return user.firebaseUid;
        }

        // 2. Check if fcmTokens contains a firebaseUid
        if (user && user.fcmTokens && user.fcmTokens.length > 0) {
            const tokenObj = user.fcmTokens.find(t => t.firebaseUid);
            if (tokenObj && tokenObj.firebaseUid) {
                // Update top-level field for faster subsequent lookups
                user.firebaseUid = tokenObj.firebaseUid;
                await user.save({ validateBeforeSave: false });
                return tokenObj.firebaseUid;
            }
        }

        // 3. Fallback: Query Firestore users collection
        if (firebaseAdmin.isFirebaseEnabled()) {
            const db = firebaseAdmin.getFirestore();
            if (db) {
                const snapshot = await db.collection('users')
                    .where('backendUserId', '==', String(mongoUserId))
                    .limit(1)
                    .get();

                if (!snapshot.empty) {
                    const firebaseUid = snapshot.docs[0].id; // document ID is Firebase UID
                    
                    // Update MongoDB top-level field
                    if (user) {
                        user.firebaseUid = firebaseUid;
                        await user.save({ validateBeforeSave: false });
                    }
                    return firebaseUid;
                }
            }
        }
    } catch (err) {
        console.warn(`[BE2][FIRESTORE] Error mapping user ID ${mongoUserId} to Firebase UID:`, err.message);
    }

    console.warn(`[BE2][FIRESTORE] Warning: Could not map MongoDB userId ${mongoUserId} to any Firebase UID.`);
    return null;
}

/**
 * Get Firestore Document Reference for a MongoDB User ID
 */
async function getUserFirestoreRefByMongoUserId(mongoUserId) {
    const firebaseUid = await findFirebaseUidByMongoUserId(mongoUserId);
    if (!firebaseUid) return null;

    const db = firebaseAdmin.getFirestore();
    return db ? db.collection('users').doc(firebaseUid) : null;
}

/**
 * Get User Profile Data from Firestore for a MongoDB User ID
 */
async function getUserProfileByMongoUserId(mongoUserId) {
    const firebaseUid = await findFirebaseUidByMongoUserId(mongoUserId);
    if (!firebaseUid) return null;

    const db = firebaseAdmin.getFirestore();
    if (!db) return null;

    const doc = await db.collection('users').doc(firebaseUid).get();
    return doc.exists ? doc.data() : null;
}

/**
 * Merge/Update User Profile Data in Firestore for a MongoDB User ID
 */
async function updateUserProfileByMongoUserId(mongoUserId, data) {
    const firebaseUid = await findFirebaseUidByMongoUserId(mongoUserId);
    if (!firebaseUid) return false;

    const db = firebaseAdmin.getFirestore();
    if (!db) return false;

    await db.collection('users').doc(firebaseUid).set(data, { merge: true });
    return true;
}

module.exports = {
    findFirebaseUidByMongoUserId,
    getUserFirestoreRefByMongoUserId,
    getUserProfileByMongoUserId,
    updateUserProfileByMongoUserId
};
