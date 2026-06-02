const DailyStats = require('../models/daily.stats.model');
const Product = require('../models/product.model');
const ChatLog = require('../models/chat.log.model');

// Helper to get today's date string YYYY-MM-DD
const getTodayStr = () => new Date().toISOString().split('T')[0];

exports.trackView = async (req, res) => {
    try {
        const { productId } = req.body;
        const today = getTodayStr();

        // 1. Increment Product Total Views
        if (productId) {
            await Product.findByIdAndUpdate(productId, { $inc: { views: 1 } });
        }

        // 2. Increment Daily Stats Views
        await DailyStats.findOneAndUpdate(
            { date: today },
            { $inc: { views: 1 } },
            { upsert: true, new: true }
        );

        res.status(200).json({ success: true });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

exports.trackAddToCart = async (req, res) => {
    try {
        const today = getTodayStr();
        
        await DailyStats.findOneAndUpdate(
            { date: today },
            { $inc: { addToCart: 1 } },
            { upsert: true, new: true }
        );

        res.status(200).json({ success: true });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

exports.trackChat = async (req, res) => {
    try {
        const { message, response_type, user_id } = req.body;
        
        // Simple keyword extraction (can be improved with AI)
        const keywords = message.toLowerCase()
            .replace(/[^\w\s]/gi, '')
            .split(' ')
            .filter(word => word.length > 3 && !['what', 'where', 'when', 'how', 'with', 'this', 'that'].includes(word));

        await ChatLog.create({
            user_id,
            message,
            keywords,
            response_type
        });

        res.status(201).json({ success: true });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};
