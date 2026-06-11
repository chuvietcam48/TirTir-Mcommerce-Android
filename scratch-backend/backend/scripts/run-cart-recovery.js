const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

/**
 * Script to manually trigger cart recovery push notifications from CLI
 * Restricts execution in production environments
 */
async function main() {
    if (process.env.NODE_ENV === 'production') {
        console.error('ERROR: CLI script execution is restricted to non-production environments.');
        process.exit(1);
    }

    try {
        console.log('Connecting to MongoDB...');
        await mongoose.connect(process.env.MONGO_URI);
        console.log('Connected to MongoDB.');

        // Initialize Firebase Admin service account through environment configs
        require('../services/firebaseAdmin.service');

        const { runCartRecovery } = require('../services/cartRecoveryFcm.service');
        const stats = await runCartRecovery();
        
        console.log('\n======================================');
        console.log('CART RECOVERY RESULTS:');
        console.log(`Scanned: ${stats.scanned}`);
        console.log(`Sent:    ${stats.sent}`);
        console.log(`Skipped: ${stats.skipped}`);
        console.log(`Errors:  ${stats.errors}`);
        console.log('======================================\n');

    } catch (error) {
        console.error('Execution error:', error);
    } finally {
        await mongoose.disconnect();
        console.log('Disconnected from DB.');
    }
}

main();
