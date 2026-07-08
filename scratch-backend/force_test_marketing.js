require('dotenv').config();
const mongoose = require('mongoose');
const admin = require('firebase-admin');
const Notification = require('./backend/models/notification.model');
const User = require('./backend/models/user.model');

// Initialize Firebase Admin
if (!admin.apps.length) {
  const serviceAccount = require('./config/serviceAccountKey.json');
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

async function run() {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log('Connected to DB');

    const title = '🔥 FLASH SALE GIỜ VÀNG!';
    const message = 'Deal chớp nhoáng giảm 50% toàn bộ mặt nạ. Số lượng có hạn, chốt ngay kẻo lỡ!';
    const link = '/products/flash-sale';

    // 1. Get all regular users
    const allUsers = await User.find({ role: 'user' });
    console.log(`Tìm thấy ${allUsers.length} users để gửi thông báo Marketing.`);

    if (allUsers.length === 0) {
      console.log('Không có user nào. Kết thúc.');
      process.exit(0);
    }

    // 2. Build and save MongoDB Notifications (In-App)
    const notifications = allUsers.map(user => ({
        user: user._id,
        type: 'promotion',
        title: title,
        message: message,
        link: link,
        isRead: false
    }));
    await Notification.insertMany(notifications);
    console.log(`✅ Đã lưu ${notifications.length} thông báo In-App vào Database!`);

    // 3. Send Push Notifications (OS Level)
    const tokens = [];
    allUsers.forEach(u => {
        if (u.fcmTokens && u.fcmTokens.length > 0) {
            u.fcmTokens.forEach(t => {
                if (t.token) tokens.push(t.token);
            });
        }
    });

    if (tokens.length > 0) {
        console.log(`Tiến hành gửi ${tokens.length} Push Notifications qua Google FCM...`);
        for (const token of tokens) {
            try {
                const response = await admin.messaging().send({
                    notification: { title, body: message },
                    data: { type: 'PROMOTION', screen: link },
                    token: token
                });
                console.log(`✅ Đã bắn Push rớt xuống máy ảo thành công:`, response);
            } catch (e) {
                console.error(`❌ Lỗi gửi Push tới token ${token.substring(0, 10)}... :`, e.message);
            }
        }
    } else {
        console.log('❌ Không tìm thấy thiết bị nào có đăng ký nhận Push (FCM Token).');
    }

  } catch(e) {
    console.error(e);
  } finally {
    process.exit(0);
  }
}

run();
