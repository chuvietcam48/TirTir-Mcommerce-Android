const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });
const mongoose = require('mongoose');

async function run() {
    try {
        await mongoose.connect(process.env.MONGO_URI);
        const users = await mongoose.connection.db.collection('users').find({}).limit(5).toArray();
        console.log(`Found ${users.length} users.`);
        users.forEach(u => {
            console.log(`- ${u.email}: Tokens: ${u.fcmTokens ? u.fcmTokens.length : 'undefined'}`);
            if (u.fcmTokens) console.log(JSON.stringify(u.fcmTokens));
        });

        const withTokens = await mongoose.connection.db.collection('users').countDocuments({ 'fcmTokens.0': { $exists: true } });
        console.log(`Users with tokens: ${withTokens}`);

    } catch (err) {
        console.error(err);
    } finally {
        await mongoose.disconnect();
        process.exit(0);
    }
}
run();
