const mongoose = require('mongoose');

const ChatLogSchema = new mongoose.Schema({
    user_id: { type: String, default: 'anonymous' },
    message: { type: String, required: true },
    keywords: [String], // Extracted keywords
    response_type: String, // 'product_recommendation', 'general', 'error'
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('ChatLog', ChatLogSchema);
