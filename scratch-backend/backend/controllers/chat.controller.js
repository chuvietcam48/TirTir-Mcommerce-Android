const axios = require('axios');
const mongoose = require('mongoose');
const ChatHistory = require('../models/chat.history.model');
const Coupon = require('../models/coupon.model');
const Order = require('../models/order.model');

const CHATBOT_SERVICE_URL = process.env.CHATBOT_SERVICE_URL || 'http://localhost:8001';
const AI_SERVICE_API_KEY = process.env.AI_SERVICE_API_KEY || '';

// ─── Helper: persist a pair of messages to the user's ChatHistory ────────────
async function saveMessagesToDB(userId, userMsg, botMsg) {
    try {
        await ChatHistory.findOneAndUpdate(
            { user: userId },
            {
                $push: {
                    messages: {
                        $each: [userMsg, botMsg],
                        // Keep at most 200 messages to cap doc size
                        $slice: -200,
                    }
                }
            },
            { upsert: true, new: true }
        );
    } catch (err) {
        // Non-fatal — log but don't break the response
        console.error('[CHAT] Failed to persist messages to DB:', err.message);
    }
}

async function getRecentConversationHistory(userId, limit = 5) {
    if (!userId) {
        return [];
    }

    try {
        const chatDoc = await ChatHistory.findOne({ user: userId })
            .select('messages')
            .lean();

        const recentMessages = (chatDoc?.messages || []).slice(-limit);
        return recentMessages
            .filter((msg) => msg?.text && msg?.sender)
            .map((msg) => ({
                role: msg.sender === 'user' ? 'user' : 'bot',
                content: String(msg.text || '').trim(),
            }))
            .filter((msg) => msg.content.length > 0);
    } catch (err) {
        console.error('[CHAT] Failed to load conversation history:', err.message);
        return [];
    }
}

async function getActiveCoupons(limit = 10) {
    try {
        const now = new Date();
        const coupons = await Coupon.find({
            active: true,
            validFrom: { $lte: now },
            validTo: { $gte: now },
            $expr: { $lt: ['$usedCount', '$usageLimit'] },
        })
            .select('code discountType discountValue minOrderValue maxDiscount validTo usedCount usageLimit')
            .sort({ validTo: 1 })
            .limit(limit)
            .lean();

        return (coupons || []).map((coupon) => ({
            code: coupon.code,
            discount_type: coupon.discountType,
            discount_value: coupon.discountValue,
            min_order_value: coupon.minOrderValue || 0,
            max_discount: coupon.maxDiscount ?? null,
            valid_to: coupon.validTo,
            remaining_uses: Math.max(0, (coupon.usageLimit || 0) - (coupon.usedCount || 0)),
        }));
    } catch (err) {
        console.error('[CHAT] Failed to load active coupons:', err.message);
        return [];
    }
}

function extractOrderCode(message = '') {
    if (!message) {
        return null;
    }

    const orderIntentRegex = /(đơn\s*hàng|mã\s*đơn|kiểm\s*tra\s*đơn|kiem\s*tra\s*don|order|tracking)/i;
    if (!orderIntentRegex.test(message)) {
        return null;
    }

    const explicitPattern = /(?:mã\s*đơn|ma\s*don|order\s*(?:id|code)?|tracking\s*(?:id|code)?|đơn\s*hàng)\s*[:#-]?\s*([A-Za-z0-9-]{6,40})/i;
    const explicitMatch = message.match(explicitPattern);
    if (explicitMatch?.[1]) {
        return explicitMatch[1].trim();
    }

    const fallbackCandidates = message.match(/[A-Za-z0-9-]{8,40}/g) || [];
    return fallbackCandidates.length > 0 ? fallbackCandidates[fallbackCandidates.length - 1] : null;
}

function extractOrderCodeFromHistory(conversationHistory = []) {
    if (!Array.isArray(conversationHistory) || conversationHistory.length === 0) {
        return null;
    }

    const explicitCodeRegex = /(ORD-[A-Za-z0-9-]{3,40}|GHN-[A-Za-z0-9-]{3,40}|GHN\d{6,}|[a-fA-F0-9]{24})/;

    for (let idx = conversationHistory.length - 1; idx >= 0; idx -= 1) {
        const content = String(conversationHistory[idx]?.content || '').trim();
        if (!content) continue;

        const explicitMatch = content.match(explicitCodeRegex);
        if (explicitMatch?.[1]) {
            return explicitMatch[1].trim();
        }

        const inferred = extractOrderCode(content);
        if (inferred) {
            return inferred;
        }
    }

    return null;
}

async function getOrderStatusContext(orderCode, userId) {
    if (!orderCode || !userId) {
        return null;
    }

    try {
        const queryOptions = [
            { user: userId, trackingNumber: orderCode },
            { user: userId, ghnOrderCode: orderCode },
        ];

        if (mongoose.Types.ObjectId.isValid(orderCode)) {
            queryOptions.unshift({ user: userId, _id: new mongoose.Types.ObjectId(orderCode) });
        }

        const order = await Order.findOne({ $or: queryOptions })
            .select('_id status orderStatus trackingNumber ghnOrderCode expectedDeliveryDate updatedAt createdAt totalAmount')
            .lean();

        if (!order) {
            return {
                order_code: orderCode,
                found: false,
            };
        }

        return {
            order_code: orderCode,
            found: true,
            order_id: String(order._id),
            status: order.status,
            shipping_status: order.orderStatus,
            tracking_number: order.trackingNumber || null,
            ghn_order_code: order.ghnOrderCode || null,
            expected_delivery_date: order.expectedDeliveryDate || null,
            updated_at: order.updatedAt,
            created_at: order.createdAt,
            total_amount: order.totalAmount,
        };
    } catch (err) {
        console.error('[CHAT] Failed to load order status:', err.message);
        return null;
    }
}

async function buildDynamicContext(message, userId, conversationHistory = []) {
    const dynamicContext = {
        active_coupons: [],
        order_status: null,
    };

    const couponIntentRegex = /(mã\s*giảm\s*giá|ma\s*giam\s*gia|voucher|coupon|khuyến\s*mãi|khuyen\s*mai|ưu\s*đãi|uu\s*dai)/i;
    const orderCode = extractOrderCode(message) || extractOrderCodeFromHistory(conversationHistory);

    if (couponIntentRegex.test(message || '')) {
        dynamicContext.active_coupons = await getActiveCoupons(10);
    }

    if (orderCode) {
        dynamicContext.order_status = await getOrderStatusContext(orderCode, userId);
    }

    return dynamicContext;
}

/**
 * @route   POST /api/v1/chat
 * @desc    Process a chatbot message via FastAPI AI Microservice.
 *          Uses optionalProtect: guests pass through, logged-in users
 *          get their conversation auto-saved to MongoDB.
 * @access  Public (guests allowed; authenticated users get DB persistence)
 */
exports.chatWithBot = async (req, res) => {
    const { message } = req.body;

    if (!message || !message.trim()) {
        return res.status(400).json({ success: false, message: 'Vui lòng nhập nội dung tin nhắn.' });
    }

    try {
        console.log(`[CHAT] Sending request to ${CHATBOT_SERVICE_URL}/chat with message: "${message.trim()}"`);
        const sessionId = req.user?._id?.toString() || `guest:${req.ip || 'unknown'}`;
        const conversationHistory = await getRecentConversationHistory(req.user?._id, 5);
        const dynamicContext = await buildDynamicContext(message.trim(), req.user?._id, conversationHistory);

        res.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
        res.setHeader('Cache-Control', 'no-cache, no-transform');
        res.setHeader('Connection', 'keep-alive');
        res.setHeader('X-Accel-Buffering', 'no');
        if (typeof res.flushHeaders === 'function') {
            res.flushHeaders();
        }

        const writeSse = (eventName, payload) => {
            res.write(`event: ${eventName}\ndata: ${JSON.stringify(payload)}\n\n`);
        };

        const response = await axios.post(
            `${CHATBOT_SERVICE_URL}/chat`,
            {
                message: message.trim(),
                session_id: sessionId,
                conversation_history: conversationHistory,
                dynamic_context: dynamicContext,
            },
            {
                timeout: 45000,
                responseType: 'stream',
                validateStatus: () => true,
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'text/event-stream',
                    ...(AI_SERVICE_API_KEY && { 'X-API-Key': AI_SERVICE_API_KEY })
                }
            }
        );

        const contentType = response.headers['content-type'] || '';
        if (response.status >= 400 || !contentType.includes('text/event-stream')) {
            let rawBody = '';
            response.data.setEncoding('utf8');
            response.data.on('data', (chunk) => {
                rawBody += chunk;
            });
            response.data.on('end', () => {
                let detail = 'Lỗi Chatbot Service';
                try {
                    const parsed = JSON.parse(rawBody || '{}');
                    detail = parsed.detail || parsed.error || parsed.message || detail;
                } catch (_) {}
                writeSse('error', { success: false, message: detail });
                res.end();
            });
            response.data.on('error', (streamErr) => {
                writeSse('error', { success: false, message: streamErr.message || 'Lỗi đọc phản hồi AI Service' });
                res.end();
            });
            return;
        }

        let buffer = '';
        let finalPayload = null;
        let streamedText = '';
        let clientClosed = false;

        req.on('close', () => {
            clientClosed = true;
            if (response.data && !response.data.destroyed) {
                response.data.destroy();
            }
        });

        const handleEventBlock = (rawEventBlock) => {
            const lines = rawEventBlock
                .split('\n')
                .map((line) => line.trim())
                .filter(Boolean);

            if (!lines.length) {
                return;
            }

            const eventLine = lines.find((line) => line.startsWith('event:'));
            const eventName = eventLine ? eventLine.slice(6).trim() : 'message';
            const dataLines = lines
                .filter((line) => line.startsWith('data:'))
                .map((line) => line.slice(5).trim());
            const dataRaw = dataLines.join('\n');

            if (!dataRaw) {
                return;
            }

            let payload = {};
            try {
                payload = JSON.parse(dataRaw);
            } catch (_) {
                payload = { text: dataRaw };
            }

            if (eventName === 'chunk') {
                const text = payload.text || '';
                if (text) {
                    streamedText += text;
                }
                writeSse('chunk', { text });
                return;
            }

            if (eventName === 'done') {
                finalPayload = payload;
                writeSse('done', payload);
                return;
            }

            if (eventName === 'error') {
                writeSse('error', payload);
                return;
            }

            writeSse(eventName, payload);
        };

        response.data.setEncoding('utf8');
        response.data.on('data', (chunk) => {
            buffer += chunk;
            let separatorIndex = buffer.indexOf('\n\n');
            while (separatorIndex !== -1) {
                const eventBlock = buffer.slice(0, separatorIndex);
                buffer = buffer.slice(separatorIndex + 2);
                handleEventBlock(eventBlock);
                separatorIndex = buffer.indexOf('\n\n');
            }
        });

        response.data.on('end', async () => {
            if (buffer.trim()) {
                handleEventBlock(buffer);
                buffer = '';
            }

            const donePayload = finalPayload || {
                success: true,
                data: {
                    intent: 'consultation',
                    message: streamedText,
                    data: null,
                    type: 'text',
                },
            };

            if (!finalPayload) {
                writeSse('done', donePayload);
            }

            if (req.user && donePayload.success && donePayload.data) {
                const botData = donePayload.data;
                const userMsg = {
                    text: message.trim(),
                    sender: 'user',
                    timestamp: new Date(),
                };
                const botMsg = {
                    text: botData.message || streamedText,
                    sender: 'bot',
                    timestamp: new Date(),
                    // Store full product data with recommendations & metadata
                    ...(botData.type === 'product' && botData.data
                        ? { productData: botData.data }
                        : {}),
                    // Store intent classification for conversation analytics
                    ...(botData.intent ? { intent: botData.intent } : {})
                };
                await saveMessagesToDB(req.user._id, userMsg, botMsg);
            }

            if (!clientClosed) {
                res.end();
            }
        });

        response.data.on('error', (streamErr) => {
            writeSse('error', { success: false, message: streamErr.message || 'Lỗi luồng AI Service' });
            if (!clientClosed) {
                res.end();
            }
        });

        return;

    } catch (error) {
        // ── Detailed diagnostics — always log so nothing is swallowed ──────────
        console.error('[CHAT] AI SERVICE ERROR DETAILS:', {
            code: error.code,
            message: error.message,
            targetUrl: `${CHATBOT_SERVICE_URL}/chat`,
            httpStatus: error.response?.status,
            httpBody: error.response?.data,
        });

        // Network error → chatbot service not running
        if (error.code === 'ECONNREFUSED' || error.code === 'ENOTFOUND') {
            res.write(`event: error\ndata: ${JSON.stringify({
                success: false,
                message: `AI Chatbot Service chưa chạy. Hãy khởi động service tại ${CHATBOT_SERVICE_URL}`,
            })}\n\n`);
            return res.end();
        }
        // Request timed out
        if (error.code === 'ECONNABORTED') {
            res.write(`event: error\ndata: ${JSON.stringify({ success: false, message: 'AI Chatbot đang quá tải, vui lòng thử lại sau.' })}\n\n`);
            return res.end();
        }
        // FastAPI returned 4xx/5xx — forward the detail message
        if (error.response) {
            res.write(`event: error\ndata: ${JSON.stringify({
                success: false,
                message: error.response.data?.detail || 'Lỗi Chatbot Service',
            })}\n\n`);
            return res.end();
        }

        console.error('[CHAT] Unexpected error:', error);
        res.write(`event: error\ndata: ${JSON.stringify({ success: false, message: 'Lỗi hệ thống AI.' })}\n\n`);
        return res.end();
    }
};

/**
 * @route   GET /api/v1/chat/history
 * @desc    Fetch authenticated user's full chat history from MongoDB.
 * @access  Private (JWT required)
 */
exports.getChatHistory = async (req, res) => {
    try {
        const chatDoc = await ChatHistory.findOne({ user: req.user._id })
            .select('messages')
            .lean();

        // Return empty array if the user has never chatted before
        const messages = chatDoc?.messages ?? [];

        return res.json({
            success: true,
            data: messages,
        });
    } catch (error) {
        console.error('[CHAT] getChatHistory error:', error.message);
        return res.status(500).json({ success: false, message: 'Failed to load chat history.' });
    }
};

/**
 * @route   DELETE /api/v1/chat/history
 * @desc    Clear the authenticated user's chat history.
 * @access  Private (JWT required)
 */
exports.clearChatHistory = async (req, res) => {
    try {
        await ChatHistory.findOneAndUpdate(
            { user: req.user._id },
            { $set: { messages: [] } }
        );
        return res.json({ success: true, message: 'Chat history cleared.' });
    } catch (error) {
        console.error('[CHAT] clearChatHistory error:', error.message);
        return res.status(500).json({ success: false, message: 'Failed to clear chat history.' });
    }
};