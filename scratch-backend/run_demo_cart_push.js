require('dotenv').config();
const admin = require('firebase-admin');

// 1. Khởi tạo Firebase Admin
if (!admin.apps.length) {
  try {
    const serviceAccount = require('./config/serviceAccountKey.json');
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  } catch(e) {
    console.error("Không tìm thấy serviceAccountKey.json");
    process.exit(1);
  }
}

async function runDemo() {
  try {
    console.log("Đang quét tìm thiết bị (Emulator) đã đăng nhập...");
    const db = admin.firestore();
    
    // Tìm trong tất cả các subcollection fcmTokens của mọi user
    const fcmTokensSnapshot = await db.collectionGroup('fcmTokens').get();
    
    if (fcmTokensSnapshot.empty) {
      console.log("❌ Không tìm thấy thiết bị nào! Hãy mở App trên Emulator, đăng nhập và đợi 5s để App lưu token.");
      process.exit(1);
    }

    // Lấy token mới nhất
    const tokenDoc = fcmTokensSnapshot.docs[fcmTokensSnapshot.docs.length - 1];
    const token = tokenDoc.data().token;

    console.log(`✅ Đã tìm thấy thiết bị! Đang gửi thông báo giỏ hàng...`);

    const message = {
      token: token,
      notification: {
        title: 'Bạn còn sản phẩm trong giỏ!',
        body: 'Bạn còn TirTir Mask Fit Red Cushion đang chờ thanh toán. Bấm vào để hoàn tất nhé!'
      },
      data: {
        screen: 'CART',
        type: 'CART_RECOVERY'
      }
    };

    const response = await admin.messaging().send(message);
    console.log("🎉 Gửi thành công! Hãy kiểm tra màn hình Emulator của bạn.");
    console.log("Message ID:", response);

  } catch (error) {
    console.error("❌ Lỗi khi gửi thông báo:", error.message);
  } finally {
    process.exit(0);
  }
}

runDemo();
