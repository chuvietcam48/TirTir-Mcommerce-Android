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
                title: 'Summer Skincare Flash Sale',
                startDate: new Date(Date.now() - 24 * 60 * 60 * 1000), // Started yesterday
                endDate: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000), // Ends in 5 days
                status: 'LIVE',
                targetRevenue: 50000000,
                currentRevenue: 12500000, // 25% progress
                message: '50% OFF All Skincare!',
                path: '/products',
                targetAudience: 'All users'
            },
            {
                title: 'Autumn Radiance Launch',
                startDate: new Date(Date.now() + 10 * 24 * 60 * 60 * 1000), // Starts in 10 days
                endDate: new Date(Date.now() + 20 * 24 * 60 * 60 * 1000),
                status: 'SCHEDULED',
                targetRevenue: 200000000,
                currentRevenue: 0,
                message: 'New autumn collection launch.',
                path: '/products/autumn',
                targetAudience: 'Premium Segment'
            }
        ]);

        // Add 3 activities
        await MarketingActivity.create([
            { type: 'success', title: 'Flash Sale Notification Sent', targetOrStatus: 'Target: 12.4k recipients' },
            { type: 'system', title: 'Voucher "LUNAR24" Created', targetOrStatus: 'Status: Active' },
            { type: 'draft', title: 'Abandoned Cart Email Drafted', targetOrStatus: 'Target: At-risk users' }
        ]);

        console.log('Seed completed successfully!');
        process.exit(0);
    })
    .catch((err) => {
        console.error('Seed error:', err);
        process.exit(1);
    });
