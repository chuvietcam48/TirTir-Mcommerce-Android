const mongoose = require('mongoose');
const dotenv = require('dotenv');
dotenv.config();

const User = require('./models/User');

const DB_URI = process.env.MONGO_URI || process.env.MONGODB_URI || 'mongodb://localhost:27017/tirtir';

mongoose.connect(DB_URI, {
    useNewUrlParser: true,
    useUnifiedTopology: true
}).then(async () => {
    console.log('Connected to DB');

    const users = await User.find().limit(10);
    
    let i = 0;
    for (let u of users) {
        // First 3 users: At risk (inactive > 30 days)
        if (i < 3) {
            u.lastActiveDate = new Date(Date.now() - 40 * 24 * 60 * 60 * 1000); // 40 days ago
            u.totalSpent = 500000 + Math.random() * 2000000;
        } 
        // Next 3 users: Slipping Away (inactive > 60 days)
        else if (i >= 3 && i < 6) {
            u.lastActiveDate = new Date(Date.now() - 75 * 24 * 60 * 60 * 1000); // 75 days ago
            u.totalSpent = 3000000 + Math.random() * 5000000;
        }
        // Rest: Active
        else {
            u.lastActiveDate = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000); // 2 days ago
            u.totalSpent = 100000 + Math.random() * 500000;
        }
        
        await u.save();
        console.log(`Updated user ${u.name} - LTV: ${u.totalSpent} - Last Active: ${u.lastActiveDate}`);
        i++;
    }

    console.log('Done seeding retention data');
    process.exit(0);
}).catch(err => {
    console.error('Error connecting to DB', err);
    process.exit(1);
});
