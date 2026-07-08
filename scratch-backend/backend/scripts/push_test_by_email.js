require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });
const mongoose = require('mongoose');
const User = require('../models/user.model');
// Manually set path for firebaseAdmin.service to find it since we are not setting it via env
process.env.FIREBASE_SERVICE_ACCOUNT_PATH = require('path').join(__dirname, '../../config/serviceAccountKey.json');

const { sendGenericPushToUser, isFirebaseEnabled } = require('../services/firebaseAdmin.service');

async function run() {
    const email = 'cee.m48@gmail.com';

    try {
        console.log(`Connecting to MongoDB...`);
        await mongoose.connect(process.env.MONGO_URI);
        console.log(`Connected successfully.`);

        const user = await User.findOne({ email: email.toLowerCase() });
        if (!user) {
            console.error(`User with email ${email} not found.`);
            process.exit(1);
        }

        console.log(`Found user: ${user.name} (${user._id})`);

        if (!user.fcmTokens || user.fcmTokens.length === 0) {
            console.warn(`User has no FCM tokens registered.`);
            process.exit(1);
        }

        const activeTokens = user.fcmTokens.filter(t => t.active !== false);
        console.log(`User has ${activeTokens.length} active FCM tokens.`);

        if (activeTokens.length === 0) {
            process.exit(1);
        }

        console.log(`Firebase Enabled: ${isFirebaseEnabled()}`);

        const title = 'TirTir Special Offer! 🌟';
        const body = 'Chào Cee! Chúng tôi có ưu đãi giảm giá 20% cho đơn hàng tiếp theo của bạn. Kiểm tra ngay!';
        const data = {
            type: 'promotion',
            promoCode: 'CEE20',
            deepLink: 'tirtir://vouchers'
        };

        console.log(`Sending notification...`);
        const result = await sendGenericPushToUser(user._id, title, body, data);
        console.log('Result:', result);

    } catch (err) {
        console.error('Error:', err);
    } finally {
        await mongoose.disconnect();
        process.exit(0);
    }
}

run();
