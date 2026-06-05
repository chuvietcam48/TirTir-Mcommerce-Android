const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const User = require('../models/user.model');

const MAIN_ADMIN_EMAIL = 'admin@tirtir.com';

// List of test admin emails to remove
const TEST_ADMIN_EMAILS = [
    'admin_product_test@tirtir.com',
    'admin_test_auto@tirtir.com',
    'admin_dashboard_test@tirtir.com',
    'stock_test_admin@tirtir.com',
    'admin_test_suite@tirtir.com',
    'admin_test_smoke@tirtir.com' // Potential smoke test email
];

async function cleanupAdmins() {
    try {
        console.log('Connecting to MongoDB...');
        await mongoose.connect(process.env.MONGO_URI);
        console.log('Connected.');

        console.log(`\nCleaning up old test admin accounts...`);
        const result = await User.deleteMany({ 
            email: { $in: TEST_ADMIN_EMAILS } 
        });
        
        console.log(`Deleted ${result.deletedCount} test admin accounts.`);
        
        // Verify main admin exists
        const mainAdmin = await User.findOne({ email: MAIN_ADMIN_EMAIL });
        if (mainAdmin) {
            console.log(`\n✅ Verified: Main Admin account (${MAIN_ADMIN_EMAIL}) exists and is active.`);
        } else {
            console.log(`\n⚠️ Warning: Main Admin account (${MAIN_ADMIN_EMAIL}) NOT found. Please run 'ensure_admin.js' first.`);
        }

    } catch (error) {
        console.error('Error during cleanup:', error);
    } finally {
        await mongoose.disconnect();
        console.log('Disconnected from DB.');
    }
}

cleanupAdmins();
