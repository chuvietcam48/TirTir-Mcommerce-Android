const cron = require('node-cron');
const User = require('../models/user.model');
const firebaseAdmin = require('../services/firebaseAdmin.service');

// Map skin types to specific tips
const SKIN_TIPS = {
    'Oily': {
        title: "Tips cho da Dầu mùa này! 🌿",
        body: "Đừng quên tẩy da chết BHA 2 lần/tuần để lỗ chân lông luôn thông thoáng nhé. Xem các sản phẩm phù hợp tại đây!",
        link: "tirtir://products/filters?skinType=Oily"
    },
    'Dry': {
        title: "Cấp ẩm sâu cho da Khô 💧",
        body: "Sử dụng serum HA trên nền da ẩm và khóa lại bằng kem dưỡng để da luôn căng bóng. Khám phá ngay!",
        link: "tirtir://products/filters?skinType=Dry"
    },
    'Combination': {
        title: "Chăm sóc da Hỗn hợp ⚖️",
        body: "Sử dụng mặt nạ đất sét cho vùng chữ T và kem dưỡng mỏng nhẹ cho vùng chữ U nhé.",
        link: "tirtir://products/filters?skinType=Combination"
    },
    'Sensitive': {
        title: "Bảo vệ làn da Nhạy cảm 🛡️",
        body: "Luôn test sản phẩm mới ở vùng xương hàm trước khi dùng toàn mặt. Xem các sản phẩm dịu nhẹ nhất!",
        link: "tirtir://products/filters?skinType=Sensitive"
    }
};

// Run every Monday at 10:00 AM
// cron format: '0 10 * * 1'
cron.schedule('0 10 * * 1', async () => {
    console.log('[CRON] Running Weekly Skin Tips FCM...');
    try {
        if (!firebaseAdmin.isFirebaseEnabled()) {
            console.log('[CRON] Firebase disabled, skipping Skin Tips.');
            return;
        }

        // Find users with skin type and valid FCM tokens
        const users = await User.find({
            "skinProfile.skinType": { $exists: true, $ne: null },
            "fcmTokens.0": { $exists: true }
        });

        console.log(`[CRON] Found ${users.length} users with skin profiles.`);

        let sentCount = 0;
        for (const user of users) {
            const skinType = user.skinProfile.skinType;
            const tip = SKIN_TIPS[skinType] || SKIN_TIPS['Sensitive']; // fallback

            if (user.fcmTokens && user.fcmTokens.length > 0) {
                await firebaseAdmin.sendPushToTokens(user.fcmTokens, {
                    notification: {
                        title: tip.title,
                        body: tip.body
                    },
                    data: {
                        type: "SKIN_TIP",
                        link: tip.link
                    }
                });
                sentCount++;
            }
        }
        
        console.log(`[CRON] Successfully sent weekly tips to ${sentCount} users.`);
    } catch (error) {
        console.error('[CRON] Error in Skin Tips Cron:', error);
    }
});
