const { GoogleGenerativeAI } = require('@google/generative-ai');
const User = require('../models/user.model');
const Order = require('../models/order.model');
const Coupon = require('../models/coupon.model');

/**
 * Robust Gemini Chat Controller
 * Handles /api/v1/chat
 */
exports.chatWithBot = async (req, res) => {
    try {
        const { message } = req.body;
        if (!message) {
            return res.status(400).json({ success: false, message: 'Message is required' });
        }

        const apiKey = process.env.GEMINI_API_KEY;
        if (!apiKey || apiKey.includes('your_')) {
            return res.status(500).json({
                success: false,
                message: 'Gemini API Key is not configured on the server.'
            });
        }

        // Initialize Gemini with the most stable model
        const genAI = new GoogleGenerativeAI(apiKey);
        // Using gemini-1.5-flash which is fast and reliable
        const model = genAI.getGenerativeModel({ model: 'gemini-1.5-flash' });

        // Prepare context for the bot (User profile, Recent Orders, Coupons)
        let contextText = "Bạn là trợ lý ảo của TirTir Cosmetics. Hãy trả lời thân thiện bằng tiếng Việt.\n";

        if (req.user) {
            const user = await User.findById(req.user.id);
            if (user) {
                contextText += `Khách hàng: ${user.name}, Loại da: ${user.skinProfile?.skinType || 'Chưa rõ'}.\n`;
            }

            const recentOrders = await Order.find({ user: req.user.id }).sort({ createdAt: -1 }).limit(3);
            if (recentOrders.length > 0) {
                contextText += "Đơn hàng gần đây:\n";
                recentOrders.forEach(o => {
                    contextText += `- Mã đơn: ${o.trackingNumber || o._id}, Trạng thái: ${o.orderStatus || o.status}\n`;
                });
            }
        }

        const activeCoupons = await Coupon.find({ active: true }).limit(5);
        if (activeCoupons.length > 0) {
            contextText += "Các mã giảm giá đang có:\n";
            activeCoupons.forEach(c => {
                contextText += `- ${c.code}: giảm ${c.discountValue}${c.discountType === 'percentage' ? '%' : ' VNĐ'}\n`;
            });
        }

        // Standard response (Non-streaming for simplicity in this fix)
        // If the client expects SSE, we handle that in a separate streamChat if needed
        // but the Android app seems to be failing even on standard POST.

        const isSse = req.headers.accept === 'text/event-stream';

        if (isSse) {
            res.writeHead(200, {
                'Content-Type': 'text/event-stream',
                'Cache-Control': 'no-cache',
                'Connection': 'keep-alive',
            });

            try {
                const result = await model.generateContentStream([
                    { text: contextText },
                    { text: message }
                ]);

                for await (const chunk of result.stream) {
                    const chunkText = chunk.text();
                    res.write(`event: chunk\ndata: ${JSON.stringify({ text: chunkText })}\n\n`);
                }

                res.write(`event: done\ndata: ${JSON.stringify({ message: 'Success' })}\n\n`);
                return res.end();
            } catch (err) {
                console.error('[CHAT] Gemini Stream Error:', err.message);
                res.write(`event: error\ndata: ${JSON.stringify({ message: 'Gemini service failed' })}\n\n`);
                return res.end();
            }
        } else {
            const result = await model.generateContent([
                { text: contextText },
                { text: message }
            ]);
            const response = await result.response;
            const text = response.text();

            return res.status(200).json({
                success: true,
                reply: text
            });
        }

    } catch (error) {
        console.error('[CHAT] Controller Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Internal server error in chat controller'
        });
    }
};

exports.getChatHistory = async (req, res) => {
    // Basic implementation for history
    return res.status(200).json({ success: true, data: [] });
};

exports.clearChatHistory = async (req, res) => {
    return res.status(200).json({ success: true });
};
