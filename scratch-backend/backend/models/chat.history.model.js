const mongoose = require('mongoose');

const MessageSchema = new mongoose.Schema({
    text: { type: String, required: true },
    sender: { type: String, enum: ['user', 'bot'], required: true },
    timestamp: { type: Date, default: Date.now },
    // Product recommendation data returned by chatbot engine
    productData: {
        // Core product info
        id: String,
        name: String,
        price: Number,
        image: String,
        desc: String,
        slug: String,
        
        // Recommendation metadata from filtering engine
        recommendations: [{
            id: String,
            name: String,
            price: Number,
            image: String,
            slug: String,
            score: Number,
        }],
        cheaper_alternative: {
            id: String,
            name: String,
            price: Number,
            image: String,
            slug: String,
        },
        
        // Filtering & scoring context
        filters: {
            skin_type: String,
            concern: String,
            budget: Number,
            category: String,
        },
        scoring_formula: String, // e.g., "0.5*concern + 0.3*skin_type + 0.2*budget"
    },
    // Intent classification from chatbot
    intent: {
        type: String,
        enum: ['greeting', 'consultation', 'shipping', 'return', 'info'],
        default: 'consultation'
    },
}, { _id: false, strict: false }); // strict: false allows additional fields gracefully

const ChatHistorySchema = new mongoose.Schema({
    user: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        unique: true, // One document per user
        index: true,
    },
    messages: {
        type: [MessageSchema],
        default: [],
    },
    updatedAt: { type: Date, default: Date.now },
});

// Auto-update `updatedAt` on every save
ChatHistorySchema.pre('findOneAndUpdate', function () {
    this.set({ updatedAt: new Date() });
});

module.exports = mongoose.model('ChatHistory', ChatHistorySchema);
