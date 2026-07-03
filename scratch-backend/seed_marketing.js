require('dotenv').config();
const mongoose = require('mongoose');
const Campaign = require('./models/Campaign');
const MarketingActivity = require('./models/MarketingActivity');

mongoose.connect(process.env.MONGO_URI || 'mongodb://localhost:27017/TirTir')
    .then(async () => {
        console.log('Connected to DB');

        await Campaign.deleteMany({});
        await MarketingActivity.deleteMany({});

        // Add 2 campaigns
        await Campaign.create([
            {
                title: 'Lunar New Year Flash Sale',
                endDate: new Date(Date.now() + 14 * 60 * 60 * 1000), // 14 hours
                status: 'LIVE',
                targetRevenue: 500000000,
                currentRevenue: 320000000
            },
            {
                title: 'Spring Skincare Bundle',
                endDate: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000), // 3 days
                status: 'LIVE',
                targetRevenue: 200000000,
                currentRevenue: 120000000
            }
        ]);

        // Add 3 activities
        await MarketingActivity.create([
            { type: 'success', title: 'Flash Sale Notification Sent', targetOrStatus: 'Target: 4,200 recipients' },
            { type: 'system', title: 'Voucher "SPRING24" Created', targetOrStatus: 'Status: Active' },
            { type: 'draft', title: 'Abandoned Cart Email Drafted', targetOrStatus: 'Target: Premium Segment' }
        ]);

        console.log('Seed completed successfully!');
        process.exit(0);
    })
    .catch((err) => {
        console.error('Seed error:', err);
        process.exit(1);
    });
