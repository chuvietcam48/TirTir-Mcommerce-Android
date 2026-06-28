const cron = require('node-cron');
const User = require('../models/user.model');
const firebaseAdmin = require('../services/firebaseAdmin.service');
const fcmService = require('../../services/fcmService');

const SKIN_TIP_BODIES = {
    'oily': "Da dầu nên ưu tiên sản phẩm mỏng nhẹ, tránh layer quá dày vào ban ngày.",
    'dry': "Da khô nên tăng cường cấp ẩm và khóa ẩm vào buổi tối.",
    'sensitive': "Da nhạy cảm nên test sản phẩm mới trên vùng nhỏ trước khi dùng toàn mặt.",
    'combination': "Da hỗn hợp nên điều chỉnh routine theo từng vùng da.",
    'normal': "Duy trì routine ổn định và chống nắng đều đặn mỗi ngày."
};

async function sendWeeklySkinTips() {
    console.log('[CRON] Running Weekly Skin Tips FCM...');
    const stats = { scanned: 0, sent: 0, skipped: 0, errors: 0 };
    
    try {
        const now = new Date();
        const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);

        const users = await User.find({
            $or: [
                { "skinProfile.skinType": { $exists: true, $ne: null } },
                { skinType: { $exists: true, $ne: null } }
            ]
        });

        stats.scanned = users.length;
        console.log(`[CRON] Found ${users.length} users with skinType profiles.`);

        for (const user of users) {
            const rawSkinType = user.skinProfile?.skinType || user.skinType || 'normal';
            const skinTypeKey = String(rawSkinType).toLowerCase();
            const body = SKIN_TIP_BODIES[skinTypeKey] || SKIN_TIP_BODIES['normal'];

            const userId = String(user._id);

            // Check 7-day cooldown via canSendNotification
            const canSend = await fcmService.canSendNotification(userId, 'SKIN_AWARE_TIP', { cooldownHours: 168 });
            if (!canSend) {
                stats.skipped++;
                continue;
            }

            if (user.lastSkinTipSentAt && user.lastSkinTipSentAt > sevenDaysAgo) {
                stats.skipped++;
                continue;
            }

            const payload = {
                notification: {
                    title: "Gợi ý chăm sóc da tuần này",
                    body
                },
                data: {
                    screen: "PRODUCT_LIST",
                    skinType: rawSkinType,
                    type: "SKIN_AWARE_TIP"
                }
            };

            try {
                const pushResult = await fcmService.sendToUser(userId, payload, { type: 'SKIN_AWARE_TIP' });
                if (pushResult && (pushResult.successCount > 0 || pushResult.success !== false)) {
                    user.lastSkinTipSentAt = new Date();
                    await user.save({ validateBeforeSave: false });
                    stats.sent++;
                } else {
                    stats.errors++;
                }
            } catch (sendErr) {
                stats.errors++;
                console.error(`[CRON] Error sending tip to user ${user._id}:`, sendErr.message);
            }
        }
    } catch (error) {
        console.error('[CRON] Error in sendWeeklySkinTips:', error);
    }
    return stats;
}

// Schedule cron job (Monday 10:00 AM) if enabled
if (process.env.SKIN_TIP_CRON_ENABLED !== 'false') {
    cron.schedule('0 10 * * 1', async () => {
        await sendWeeklySkinTips();
    });
}

module.exports = {
    sendWeeklySkinTips
};

