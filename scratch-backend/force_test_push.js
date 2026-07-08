require('dotenv').config();
const mongoose = require('mongoose');
const fcmService = require('./services/fcmService');
const User = require('./models/User');
const admin = require('firebase-admin');

// Initialize Firebase Admin if not already initialized
if (!admin.apps.length) {
  try {
    const serviceAccount = require('./config/serviceAccountKey.json');
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  } catch(e) {
    admin.initializeApp({ credential: admin.credential.applicationDefault() });
  }
}

async function run() {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log('Connected to DB');

    const allUsers = await User.find({});
    console.log(`Tổng số user trong DB: ${allUsers.length}`);
    const usersWithToken = allUsers.filter(u => u.fcmTokens && u.fcmTokens.length > 0);
    console.log(`Số user có fcmTokens: ${usersWithToken.length}`);

    if (allUsers.length > 0) {
      console.log('Test field of first user:', Object.keys(allUsers[0].toObject()));
    }

    const user = usersWithToken.length > 0 ? usersWithToken[0] : null;

    if (!user) {
      console.log('❌ No user with an FCM token found. Please login on the App first to register your device token.');
      process.exit(1);
    }

    console.log(`Sending test Cart Recovery Push Notification to user: ${user.email || user._id}`);

    console.log(`Sending test Cart Recovery Push Notification to user: ${user.email}`);
    
    try {
      const Notification = require('./backend/models/notification.model.js');
      
      const tokens = user.fcmTokens.map(t => t.token);
      for (const token of tokens) {
        const message = {
          notification: {
            title: 'Bạn để quên gì đó này!',
            body: 'Giỏ hàng của bạn đang có sản phẩm chờ thanh toán. Nhanh tay chốt đơn nhé!'
          },
          data: {
            type: 'CART_RECOVERY',
            screen: 'cart'
          },
          token: token
        };
        const response = await admin.messaging().send(message);
        console.log(`✅ Push Result for token ${token.substring(0, 10)}... :`, response);
      }

      await Notification.create({
        user: user._id,
        type: 'promotion', // Or 'system'/'order' based on the schema enum
        title: 'Bạn để quên gì đó này!',
        message: 'Giỏ hàng của bạn đang có sản phẩm chờ thanh toán. Nhanh tay chốt đơn nhé!',
        isRead: false
      });
      console.log('✅ Đã lưu Notification vào Database (In-App Notification)!');

    } catch (error) {
      console.log('❌ Lỗi gửi Push:', error);
    }

  } catch(e) {
    console.error(e);
  } finally {
    process.exit(0);
  }
}

run();
