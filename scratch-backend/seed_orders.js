const mongoose = require('mongoose');
const Order = require('./models/Order');
const User = require('./models/User');
require('dotenv').config();

mongoose.connect(process.env.MONGO_URI).then(async () => {
    try {
        console.log('Seeding demo orders...');
        
        // Lấy admin user để làm user tạm cho order nếu cần
        const user = await User.findOne({ role: 'user' });
        if (!user) {
            console.log('No normal user found. Please create one first.');
            process.exit(1);
        }

        // Tạo 30 orders để đạt 20% target (150)
        const mockOrders = [];
        for (let i = 0; i < 30; i++) {
            mockOrders.push({
                userId: user._id,
                status: ['Pending', 'Processing', 'Shipped', 'Delivered'][Math.floor(Math.random() * 4)],
                totalPrice: Math.floor(Math.random() * 500000) + 100000,
                shippingFee: 30000,
                paymentMethod: 'COD',
                isPaid: Math.random() > 0.5,
                shippingAddress: {
                    fullName: 'Demo User ' + i,
                    phone: '090123456' + (i % 10),
                    address: '123 Demo Street',
                    city: 'HCM'
                },
                items: [
                    {
                        product: new mongoose.Types.ObjectId().toString(),
                        name: 'Mock Product ' + i,
                        quantity: 1,
                        price: 150000,
                        shade: ''
                    }
                ],
                createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000)
            });
        }

        await Order.insertMany(mockOrders);
        console.log('Seeded 30 mock orders successfully! Dashboard will show 20% progress.');
        process.exit(0);
    } catch (err) {
        console.error(err);
        process.exit(1);
    }
});
