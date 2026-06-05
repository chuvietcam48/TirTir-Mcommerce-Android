/**
 * Migration: Remove COD paymentMethod from Orders
 *
 * Background: COD was removed from the accepted payment methods.
 * Any existing orders with paymentMethod: 'COD' cause Mongoose validation errors
 * when the order is modified (e.g., status updates, GHN webhook).
 *
 * This script migrates those orders to paymentMethod: 'MOMO' (since they were
 * cash-on-delivery test orders, not real payment transactions).
 *
 * Run: node scripts/migrate-remove-cod.js
 */

require('dotenv').config({ path: require('path').join(__dirname, '../.env') });
const mongoose = require('mongoose');

async function migrate() {
    await mongoose.connect(process.env.MONGO_URI);
    console.log('✅ MongoDB connected');

    // Use native MongoDB updateMany to bypass Mongoose validation
    const db = mongoose.connection.db;
    const result = await db.collection('orders').updateMany(
        { paymentMethod: 'COD' },
        { $set: { paymentMethod: 'MOMO' } }
    );

    console.log(`✅ Migrated ${result.modifiedCount} order(s): COD → MOMO`);
    console.log(`   (${result.matchedCount} matched, ${result.modifiedCount} modified)`);

    await mongoose.disconnect();
    console.log('🔌 Disconnected');
}

migrate().catch(err => {
    console.error('❌ Migration failed:', err.message);
    process.exit(1);
});
