const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });
const mongoose = require('mongoose');

// Use absolute path for reliability
process.env.FIREBASE_SERVICE_ACCOUNT_PATH = path.join(__dirname, '../../config/serviceAccountKey.json');

const { sendGenericPushToUser, isFirebaseEnabled } = require('../services/firebaseAdmin.service');
const User = require('../models/user.model');

async function run() {
    const email = 'cee.m48@gmail.com';
    try {
        console.log('Connecting to database...');
        await mongoose.connect(process.env.MONGO_URI);
        console.log('Connected.');

        const user = await User.findOne({ email: email.toLowerCase() });
        if (!user) {
            console.log(`User ${email} not found.`);
            return;
        }

        console.log(`Found user: ${user.name} (${user._id})`);

        const activeTokens = (user.fcmTokens || []).filter(t => t.active !== false);
        console.log('Active FCM Tokens:', activeTokens.length);

        console.log('Firebase Enabled:', isFirebaseEnabled());

        const payload = {
            title: 'TirTir Special Reward! 🎁',
            body: 'Hello Cee! You have a special gift waiting in your voucher wallet. Check it out now!',
            data: {
                type: 'promotion',
                screen: 'VOUCHER_WALLET',
                promoCode: 'CEE_SPECIAL_50'
            }
        };

        console.log('Sending push notification...');
        // Note: Even if activeTokens is 0, we call it to see the log/response
        const result = await sendGenericPushToUser(user._id, payload.title, payload.body, payload.data);
        console.log('Result:', JSON.stringify(result, null, 2));

    } catch (err) {
        console.error('Error:', err);
    } finally {
        await mongoose.disconnect();
    }
}

run();
