const mongoose = require('mongoose');
const dotenv = require('dotenv');
dotenv.config();

const Campaign = require('./models/Campaign');

const DB_URI = process.env.MONGO_URI || process.env.MONGODB_URI || 'mongodb://localhost:27017/tirtir';

mongoose.connect(DB_URI, {
    useNewUrlParser: true,
    useUnifiedTopology: true
}).then(async () => {
    console.log('Connected to DB');

    // Create a few dummy campaigns (Flash Sales)
    const campaigns = [
        {
            title: "50% OFF All Skincare",
            message: "Don't miss out on our biggest skincare sale of the year!",
            path: "/products/skincare",
            targetAudience: "All users",
            startDate: new Date(),
            endDate: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000), // 2 days
            status: "LIVE",
            targetRevenue: 50000,
            currentRevenue: 15200
        },
        {
            title: "Weekend Makeup Flash Sale",
            message: "Get 20% off all makeup items this weekend only.",
            path: "/products/makeup",
            targetAudience: "VIP users",
            startDate: new Date(Date.now() - 24 * 60 * 60 * 1000),
            endDate: new Date(Date.now() + 12 * 60 * 60 * 1000), // 12 hours
            status: "LIVE",
            targetRevenue: 10000,
            currentRevenue: 8400
        }
    ];

    for (let c of campaigns) {
        const camp = new Campaign(c);
        await camp.save();
        console.log(`Saved campaign: ${c.title}`);
    }

    console.log('Done seeding flash sales');
    process.exit(0);
}).catch(err => {
    console.error('Error connecting to DB', err);
    process.exit(1);
});
