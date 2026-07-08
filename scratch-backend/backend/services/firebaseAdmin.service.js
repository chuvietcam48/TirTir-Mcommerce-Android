const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');
const User = require('../models/user.model');

let firebaseEnabled = false;

try {
    const projectId = process.env.FIREBASE_PROJECT_ID;
    const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
    const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;

    let credential = null;

    if (serviceAccountJson) {
        try {
            const parsed = JSON.parse(serviceAccountJson);
            credential = admin.credential.cert(parsed);
            console.log('Firebase Admin: Initializing via FIREBASE_SERVICE_ACCOUNT_JSON env variable');
        } catch (e) {
            console.error('Firebase Admin: Error parsing service account JSON env variable:', e.message);
        }
    } else if (serviceAccountPath) {
        try {
            const absolutePath = path.isAbsolute(serviceAccountPath) 
                ? serviceAccountPath 
                : path.join(__dirname, '..', serviceAccountPath);
                
            if (fs.existsSync(absolutePath)) {
                const parsed = JSON.parse(fs.readFileSync(absolutePath, 'utf8'));
                credential = admin.credential.cert(parsed);
                console.log(`Firebase Admin: Initializing via JSON file at: ${absolutePath}`);
            } else {
                console.warn(`Firebase Admin: Config file not found at path: ${absolutePath}`);
            }
        } catch (e) {
            console.error('Firebase Admin: Error reading config file from path:', e.message);
        }
    }

    if (!credential) {
        const fallbackPath = path.join(__dirname, '../../config/serviceAccountKey.json');
        if (fs.existsSync(fallbackPath)) {
            try {
                const parsed = JSON.parse(fs.readFileSync(fallbackPath, 'utf8'));
                credential = admin.credential.cert(parsed);
                console.log(`Firebase Admin: Initializing via fallback JSON file at: ${fallbackPath}`);
            } catch (e) {
                console.error('Firebase Admin: Error reading fallback config file:', e.message);
            }
        }
    }

    const storageBucket = process.env.FIREBASE_STORAGE_BUCKET;
    if (credential) {
        if (!admin.apps.length) {
            admin.initializeApp({
                credential,
                projectId,
                ...(storageBucket ? { storageBucket } : {})
            });
        }
        firebaseEnabled = true;
        console.log('Firebase Admin: Ready and configured.');
    } else if (projectId) {
        // Fallback to application default credentials if project ID is provided
        try {
            admin.initializeApp({
                credential: admin.credential.applicationDefault(),
                projectId,
                ...(storageBucket ? { storageBucket } : {})
            });
            firebaseEnabled = true;
            console.log('Firebase Admin: Initialized using applicationDefault.');
        } catch (e) {
            console.warn('Firebase Admin: Failed initialization using applicationDefault:', e.message);
        }
    } else {
        console.warn('Firebase Admin: Missing environment config credentials. Push notifications will be disabled.');
    }
} catch (error) {
    console.error('Firebase Admin Initialization Failed:', error.message);
}

/**
 * Check if Firebase is enabled.
 */
function isFirebaseEnabled() {
    return firebaseEnabled;
}

/**
 * Send a push notification to specific FCM tokens.
 * Deduplicates tokens and handles multicast payload.
 */
async function sendPushToTokens(tokens, payload) {
    if (!firebaseEnabled) {
        return { skipped: true, reason: 'Firebase Admin not configured' };
    }

    // Filter out falsy/empty tokens and deduplicate
    const uniqueTokens = [...new Set(tokens.filter(t => t && typeof t === 'string' && t.trim() !== ''))];
    if (uniqueTokens.length === 0) {
        return { successCount: 0, failureCount: 0, reason: 'No valid tokens' };
    }

    // Build the message payload
    // Note: data payload fields must be string values
    const dataPayload = {};
    if (payload.data) {
        for (const [key, value] of Object.entries(payload.data)) {
            dataPayload[key] = String(value);
        }
    }

    const message = {
        tokens: uniqueTokens,
        notification: {
            title: payload.title,
            body: payload.body
        },
        data: dataPayload,
        android: {
            priority: 'high',
            notification: {
                sound: 'default',
                defaultSound: true
            }
        },
        apns: {
            payload: {
                aps: {
                    sound: 'default'
                }
            }
        }
    };

    try {
        const response = await admin.messaging().sendEachForMulticast(message);
        console.log(`FCM Multicast Sent: successCount=${response.successCount}, failureCount=${response.failureCount}`);
        
        // Log invalid tokens if any and mark them inactive
        if (response.failureCount > 0) {
            response.responses.forEach(async (resp, idx) => {
                if (!resp.success) {
                    const error = resp.error;
                    console.warn(`FCM send failure for token [${uniqueTokens[idx].slice(0, 10)}...]: ${error.code} - ${error.message}`);
                    if (error.code === 'messaging/registration-token-not-registered' || error.code === 'messaging/invalid-registration-token') {
                        try {
                            await User.updateOne(
                                { 'fcmTokens.token': uniqueTokens[idx] },
                                { $set: { 'fcmTokens.$.active': false } }
                            );
                            console.log(`[BE2][FCM] Inactivated unregistered token: ${uniqueTokens[idx].slice(0, 6)}...`);
                        } catch (dbErr) {
                            console.error(`[BE2][FCM] Error inactivating unregistered token:`, dbErr.message);
                        }
                    }
                }
            });
        }
        
        return {
            successCount: response.successCount,
            failureCount: response.failureCount,
            responses: response.responses
        };
    } catch (error) {
        console.error('FCM Multicast error:', error.message);
        return { successCount: 0, failureCount: uniqueTokens.length, error: error.message };
    }
}

/**
 * Send push notification to a user based on their stored active FCM tokens.
 */
async function sendPushToUser(userId, payload) {
    try {
        const user = await User.findById(userId);
        if (!user || !user.fcmTokens || user.fcmTokens.length === 0) {
            return { skipped: true, reason: 'User not found or has no FCM tokens' };
        }

        // Get active tokens
        const activeTokens = user.fcmTokens
            .filter(t => t.active !== false)
            .map(t => t.token);

        if (activeTokens.length === 0) {
            return { skipped: true, reason: 'No active FCM tokens found for user' };
        }

        return await sendPushToTokens(activeTokens, payload);
    } catch (err) {
        console.error(`Error sending push to user ${userId}:`, err.message);
        return { skipped: true, error: err.message };
    }
}

/**
 * Specific helper to send order placement success notifications
 */
async function sendOrderSuccessPush(userId, order) {
    const shortId = order._id.toString().slice(-6).toUpperCase();
    return await sendPushToUser(userId, {
        title: 'Đặt hàng thành công',
        body: `Đơn hàng ${order.orderCode || shortId} của bạn đã được tạo thành công.`,
        data: {
            type: 'order_success',
            orderId: String(order._id),
            orderCode: order.orderCode || shortId,
            deepLink: `tirtir://orders/${order._id}`,
            orderTotal: String(order.totalAmount || '')
        }
    });
}

/**
 * Specific helper to send order status updates notifications
 */
async function sendOrderStatusPush(userId, order) {
    const shortId = order._id.toString().slice(-6).toUpperCase();
    
    const statusMap = {
        'Processing': 'Đơn hàng đã được xác nhận',
        'Shipped': 'Đơn hàng đang được giao',
        'Delivered': 'Đơn hàng đã giao thành công',
        'Cancelled': 'Đơn hàng đã bị hủy'
    };

    const statusBodyMap = {
        'Processing': `Đơn hàng #${shortId} của bạn đã được xác nhận và đang chuẩn bị.`,
        'Shipped': `Đơn hàng #${shortId} của bạn đã được giao cho đơn vị vận chuyển.`,
        'Delivered': `Đơn hàng #${shortId} của bạn đã được giao thành công. Cảm ơn bạn!`,
        'Cancelled': `Đơn hàng #${shortId} của bạn đã bị hủy.`
    };

    const title = statusMap[order.status] || 'Cập nhật đơn hàng';
    const body = statusBodyMap[order.status] || `Trạng thái đơn hàng #${shortId} đã chuyển sang ${order.status}`;

    return await sendPushToUser(userId, {
        title,
        body,
        data: {
            type: 'order_status',
            status: order.status,
            orderId: String(order._id),
            orderCode: order.orderCode || shortId,
            deepLink: `tirtir://orders/${order._id}`
        }
    });
}

/**
 * Send a test notification to verified active tokens of the user
 */
async function sendTestPush(userId) {
    return await sendPushToUser(userId, {
        title: 'TirTir Test Push',
        body: 'FCM is working successfully.',
        data: {
            type: 'test',
            timestamp: String(Date.now())
        }
    });
}

function getFirestore() {
    if (!firebaseEnabled) return null;
    return admin.firestore();
}

function getMessaging() {
    if (!firebaseEnabled) return null;
    return admin.messaging();
}

async function sendVoucherPush(userId, voucher) {
    const discountStr = voucher.discountPct ? `${voucher.discountPct}%` : (voucher.discountValue ? `${voucher.discountValue}đ` : '');
    const expDays = voucher.expiryDays || 7;
    const code = voucher.voucherCode || voucher.code || '';
    return await sendPushToUser(userId, {
        title: "Ưu đãi dành riêng cho bạn! 🎁",
        body: `Mã giảm ${discountStr} hết hạn sau ${expDays} ngày. Sử dụng mã: ${code}`,
        data: {
            voucherCode: String(code),
            screen: "VOUCHER_WALLET",
            type: "VOUCHER_AT_RISK"
        }
    });
}

async function sendLoyaltyTierPush(userId, tier) {
    return await sendPushToUser(userId, {
        title: `Chúc mừng bạn đã lên hạng ${tier}!`,
        body: `Bạn vừa đạt hạng ${tier} trong chương trình Loyalty của TirTir.`,
        data: {
            screen: "LOYALTY",
            type: "LOYALTY_TIER_UP",
            tier: String(tier)
        }
    });
}


async function sendCartRecoveryPush(userId, cart) {
    const firstItemName = cart.items?.[0]?.name || "sản phẩm";
    return await sendPushToUser(userId, {
        title: "Bạn quên sản phẩm trong giỏ hàng 🛒",
        body: `Bạn còn ${firstItemName} trong giỏ! Quay lại hoàn tất đơn hàng nhé.`,
        data: {
            screen: "CART",
            type: "CART_RECOVERY"
        }
    });
}

async function sendGenericPushToUser(userId, title, body, data) {
    return await sendPushToUser(userId, {
        title,
        body,
        data: data || {}
    });
}

module.exports = {
    isFirebaseEnabled,
    getFirestore,
    getMessaging,
    sendPushToTokens,
    sendPushToUser,
    sendOrderSuccessPush,
    sendOrderStatusPush,
    sendVoucherPush,
    sendLoyaltyTierPush,
    sendCartRecoveryPush,
    sendGenericPushToUser,
    sendTestPush
};
