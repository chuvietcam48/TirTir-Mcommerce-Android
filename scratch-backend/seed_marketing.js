require('dotenv').config();
const mongoose = require('mongoose');

async function seed() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log('Connected to DB');

  const Campaign = require('./models/Campaign');
  const MarketingActivity = require('./models/MarketingActivity');

  await Campaign.deleteMany({});
  await MarketingActivity.deleteMany({});

  await Campaign.insertMany([
    {
      title: 'Summer Glow Flash Sale',
      startDate: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
      endDate:   new Date(Date.now() + 5 * 24 * 60 * 60 * 1000),
      status: 'LIVE',
      targetRevenue:   50000000,
      currentRevenue:  18500000,
      message: '50% OFF All Skincare — Today Only!',
      path: '/products',
      targetAudience: 'All users'
    },
    {
      title: 'Lunar New Year Giveaway',
      startDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
      endDate:   new Date(Date.now() + 1 * 24 * 60 * 60 * 1000),
      status: 'LIVE',
      targetRevenue:   30000000,
      currentRevenue:  27500000,
      message: 'Lucky draws every hour — Join now!',
      path: '/home',
      targetAudience: 'Loyalty Tier Gold+'
    },
    {
      title: 'Spring Radiance New Launch',
      startDate: new Date(Date.now() + 8 * 24 * 60 * 60 * 1000),
      endDate:   new Date(Date.now() + 20 * 24 * 60 * 60 * 1000),
      status: 'SCHEDULED',
      targetRevenue:   200000000,
      currentRevenue:  0,
      message: 'Exclusive spring collection — Coming soon.',
      path: '/products',
      targetAudience: 'Premium Segment'
    }
  ]);

  await MarketingActivity.insertMany([
    { type: 'success', title: 'Flash Sale Notification Sent',       targetOrStatus: 'Target: 12,400 recipients' },
    { type: 'success', title: 'Voucher "SUMMER25" Activated',        targetOrStatus: 'Status: 248 uses so far' },
    { type: 'system',  title: 'Abandoned Cart Email Scheduled',      targetOrStatus: 'Target: At-risk users (307)' },
    { type: 'draft',   title: 'Re-engagement Campaign Drafted',      targetOrStatus: 'Status: Awaiting approval' },
    { type: 'success', title: 'Product Review Push Sent',            targetOrStatus: 'Target: Recent buyers' }
  ]);

  console.log('✅ Marketing data seeded successfully');
  await mongoose.disconnect();
  process.exit(0);
}

seed().catch(e => { console.error(e); process.exit(1); });
