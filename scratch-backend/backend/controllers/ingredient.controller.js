const admin = require('firebase-admin');

const MAX_HISTORY = 20;

function getDb() {
    return admin.firestore();
}

// POST /api/v1/ingredient/scan-history
// Body: { userId, productName, ingredients, result }
// Saves to Firestore users/{userId}/scan_history/{autoId}
// Enforces max 20 records FIFO (removes oldest when limit exceeded)
exports.saveScanHistory = async (req, res) => {
    try {
        const { userId, productName, ingredients, result } = req.body;
        if (!userId || !productName) {
            return res.status(400).json({ message: 'userId và productName là bắt buộc' });
        }

        const db = getDb();
        const colRef = db.collection(`users/${userId}/scan_history`);

        // Add new record
        await colRef.add({
            productName,
            ingredients: ingredients || [],
            result: result || null,
            scannedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        // Enforce FIFO max 20: get all docs sorted asc, delete oldest if > MAX_HISTORY
        const snapshot = await colRef.orderBy('scannedAt', 'asc').get();
        if (snapshot.size > MAX_HISTORY) {
            const excess = snapshot.size - MAX_HISTORY;
            const batch = db.batch();
            snapshot.docs.slice(0, excess).forEach(doc => batch.delete(doc.ref));
            await batch.commit();
        }

        res.status(201).json({ message: 'Đã lưu lịch sử scan thành công' });
    } catch (error) {
        console.error('Save Scan History Error:', error);
        res.status(500).json({ message: 'Lỗi khi lưu lịch sử scan' });
    }
};

// GET /api/v1/ingredient/scan-history?userId={uid}
// Returns scan history sorted desc by scannedAt
exports.getScanHistory = async (req, res) => {
    try {
        const { userId } = req.query;
        if (!userId) {
            return res.status(400).json({ message: 'userId là bắt buộc' });
        }

        const db = getDb();
        const snapshot = await db
            .collection(`users/${userId}/scan_history`)
            .orderBy('scannedAt', 'desc')
            .limit(MAX_HISTORY)
            .get();

        const history = snapshot.docs.map(doc => ({
            id: doc.id,
            ...doc.data(),
            scannedAt: doc.data().scannedAt?.toDate?.()?.toISOString() || null
        }));

        res.json({ total: history.length, history });
    } catch (error) {
        console.error('Get Scan History Error:', error);
        res.status(500).json({ message: 'Lỗi khi lấy lịch sử scan' });
    }
};
