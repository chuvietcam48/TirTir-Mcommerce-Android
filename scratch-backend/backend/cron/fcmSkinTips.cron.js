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

async function sendWeeklySkinTips() {
    console.log('[CRON] Running Weekly Skin Tips FCM...');
    const stats = { scanned: 0, sent: 0, skipped: 0, errors: 0 };
    
    try {
        if (!firebaseAdmin.isFirebaseEnabled()) {
            console.log('[CRON] Firebase disabled, skipping Skin Tips.');
            return stats;
        }

        const now = new Date();
        const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);

        // Find users with skin type, valid FCM tokens, and who haven't received a tip in the last 7 days
        const users = await User.find({
            "skinProfile.skinType": { $exists: true, $ne: null },
            "fcmTokens.0": { $exists: true },
            $or: [
                { lastSkinTipSentAt: { $exists: false } },
                { lastSkinTipSentAt: null },
                { lastSkinTipSentAt: { $lte: sevenDaysAgo } }
            ]
        });

        stats.scanned = users.length;
        console.log(`[CRON] Found ${users.length} users due for skin tips.`);

        for (const user of users) {
            const skinType = user.skinProfile.skinType;
            const tip = SKIN_TIPS[skinType] || SKIN_TIPS['Sensitive']; // fallback

            const activeTokens = user.fcmTokens.filter(t => t.active !== false).map(t => t.token);
            if (activeTokens.length > 0) {
                try {
                    const pushResult = await firebaseAdmin.sendPushToTokens(activeTokens, {
                        title: tip.title,
                        body: tip.body,
                        data: {
                            type: "skin_tip",
                            screen: "home",
                            link: tip.link
                        }
                    });
                    
                    if (pushResult && pushResult.successCount > 0) {
                        user.lastSkinTipSentAt = new Date();
                        await user.save({ validateBeforeSave: false });
                        stats.sent++;
                        console.log(`[CRON] Sent skin tip to user ${user._id}`);
                    } else {
                        stats.errors++;
                        console.warn(`[CRON] Failed to deliver weekly tip to user ${user._id}`);
                    }
                } catch (sendErr) {
                    stats.errors++;
                    console.error(`[CRON] Error sending tip to user ${user._id}:`, sendErr.message);
                }
            } else {
                stats.skipped++;
            }
        }
    } catch (error) {
        console.error('[CRON] Error in sendWeeklySkinTips:', error);
    }
    return stats;
}

// Run every Monday at 10:00 AM
// cron format: '0 10 * * 1'
cron.schedule('0 10 * * 1', async () => {
    await sendWeeklySkinTips();
});

module.exports = {
    sendWeeklySkinTips
};
