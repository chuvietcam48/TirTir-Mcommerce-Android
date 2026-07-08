const mongoose = require('mongoose');
const dotenv = require('dotenv');
const Campaign = require('../models/campaign.model');
const MarketingActivity = require('../models/marketing_activity.model');
const Coupon = require('../models/coupon.model');
const RecoveryExperiment = require('../models/recovery_experiment.model');
const User = require('../models/user.model');
const Order = require('../models/order.model');
const Product = require('../models/product.model');

dotenv.config();

const seedData = async () => {
    try {
        await mongoose.connect(process.env.MONGO_URI);
        console.log('Connected to MongoDB for seeding marketing data...');

        // 1. Fetch a product for order items
        const sampleProduct = await Product.findOne();
        if (!sampleProduct) {
            console.log('Error: No products found in DB. Please seed products first.');
            process.exit(1);
        }

        // 2. Clear existing marketing data
        await Campaign.deleteMany({});
        await MarketingActivity.deleteMany({});
        await RecoveryExperiment.deleteMany({});

        // Clear dummy users and orders for churn simulation (ONLY dummy ones)
        const dummyUserEmails = [
            'dummy_marketing_champion@test.com',
            'dummy_marketing_atrisk@test.com',
            'dummy_marketing_churned@test.com'
        ];
        const dummyUsers = await User.find({ email: { $in: dummyUserEmails } });
        const dummyUserIds = dummyUsers.map(u => u._id);
        await Order.deleteMany({ user: { $in: dummyUserIds } });
        await User.deleteMany({ email: { $in: dummyUserEmails } });

        // 3. Seed Campaigns
        const campaigns = [
            {
                title: 'Summer Radiant Glow 2024',
                status: 'Active',
                currentRevenue: 1250000,
                targetRevenue: 5000000,
                startDate: new Date(),
                endDate: new Date(Date.now() + 86400000 * 30),
                description: 'Promoting sunscreen and hydration sets for summer.',
                type: 'Holiday'
            },
            {
                title: 'Weekend Flash Sale: 20% OFF',
                status: 'Active',
                currentRevenue: 450000,
                targetRevenue: 1000000,
                startDate: new Date(),
                endDate: new Date(Date.now() + 86400000 * 2),
                description: 'Limited time flash sale for all cushions.',
                type: 'Flash Sale'
            },
            {
                title: 'New Milk Ampoule Launch',
                status: 'Paused',
                currentRevenue: 0,
                targetRevenue: 2500000,
                startDate: new Date(Date.now() + 86400000 * 10),
                endDate: new Date(Date.now() + 86400000 * 40),
                description: 'Pre-launch campaign for Ceramic Milk Ampoule.',
                type: 'Product Launch'
            },
            {
                title: 'Winter Skincare Essentials',
                status: 'Completed',
                currentRevenue: 5200000,
                targetRevenue: 4000000,
                startDate: new Date(Date.now() - 86400000 * 60),
                endDate: new Date(Date.now() - 86400000 * 30),
                description: 'Winter heavy moisturizers promotion.',
                type: 'Discount'
            }
        ];
        await Campaign.insertMany(campaigns);
        console.log('Campaigns seeded.');

        // 4. Seed Marketing Activities
        const activities = [
            {
                title: 'Sent Summer Campaign Email',
                targetOrStatus: 'Target: 5,000 users',
                type: 'campaign',
                status: 'Success'
            },
            {
                title: 'Flash Sale Notification Triggered',
                targetOrStatus: '3,200 devices reached',
                type: 'promotion',
                status: 'Success'
            },
            {
                title: 'Cart Recovery Sequence - A/B Test',
                targetOrStatus: 'Running on 1,500 carts',
                type: 'system',
                status: 'Success'
            },
            {
                title: 'Banner Update: Holiday Collection',
                targetOrStatus: 'Home Screen Live',
                type: 'system',
                status: 'Success'
            }
        ];
        await MarketingActivity.insertMany(activities);
        console.log('Marketing Activities seeded.');

        // 5. Seed Churn/RFM Data
        console.log('Seeding dummy users for Churn analysis...');

        const commonShipping = {
            fullName: 'Dummy Receiver',
            phone: '0909000000',
            address: '123 Test St',
            city: 'Ho Chi Minh'
        };

        const createDummyOrder = async (userId, daysAgo, amount) => {
            return await Order.create({
                user: userId,
                totalAmount: amount,
                paymentMethod: 'CARD',
                shippingAddress: commonShipping,
                status: 'Delivered',
                orderStatus: 'DELIVERED',
                items: [{
                    product: sampleProduct._id,
                    name: sampleProduct.Name,
                    quantity: 1,
                    price: amount
                }],
                createdAt: new Date(Date.now() - (daysAgo * 86400000))
            });
        };

        // a. Champion User (Many orders, recent)
        const champion = await User.create({
            name: 'Alex Champion',
            email: 'dummy_marketing_champion@test.com',
            password: 'password123',
            role: 'user'
        });
        for(let i=0; i<6; i++) {
            await createDummyOrder(champion._id, i * 2, 150);
        }

        // b. At Risk User (Bought before, but quiet for 35 days)
        const atRisk = await User.create({
            name: 'Sam AtRisk',
            email: 'dummy_marketing_atrisk@test.com',
            password: 'password123',
            role: 'user'
        });
        await createDummyOrder(atRisk._id, 35, 85);
        await createDummyOrder(atRisk._id, 45, 95);

        // c. Churned User (Quiet for 70 days)
        const churned = await User.create({
            name: 'Casey Churned',
            email: 'dummy_marketing_churned@test.com',
            password: 'password123',
            role: 'user'
        });
        await createDummyOrder(churned._id, 70, 200);
        await createDummyOrder(churned._id, 80, 120);

        console.log('Churn/RFM dummy data seeded.');

        // 6. Seed Recovery Experiments
        const experiments = [
            {
                name: 'Discount vs Free Ship Recovery',
                status: 'running',
                variants: [
                    { name: '10% Discount Code', weight: 50, config: { discount: 10 } },
                    { name: 'Free Shipping Code', weight: 50, config: { free_ship: true } }
                ],
                primary_metric: 'Conversion Rate',
                start_date: new Date(),
                end_date: new Date(Date.now() + 86400000 * 14)
            }
        ];
        await RecoveryExperiment.insertMany(experiments);
        console.log('Recovery Experiments seeded.');

        console.log('All marketing data seeded successfully!');
        process.exit(0);
    } catch (err) {
        console.error('Seeding error:', err);
        process.exit(1);
    }
};

seedData();
