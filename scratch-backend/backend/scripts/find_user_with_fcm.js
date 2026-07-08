require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });
const mongoose = require('mongoose');
const User = require('../models/user.model');

async function run() {
    try {
        await mongoose.connect(process.env.MONGO_URI);
        const user = await User.findOne({ 'fcmTokens.0': { $exists: true } });
        if (user) {
            console.log(`User with FCM tokens: ${user.email} (${user.name})`);
            console.log(`Tokens count: ${user.fcmTokens.length}`);
        } else {
            console.log('No users found with FCM tokens.');
        }
    } catch (err) {
        console.error(err);
    } finally {
        await mongoose.disconnect();
        process.exit(0);
    }
}
run();
