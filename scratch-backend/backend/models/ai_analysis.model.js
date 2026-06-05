const mongoose = require('mongoose');

const AiAnalysisSchema = new mongoose.Schema({
    user: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true
    },
    imageUrl: {
        type: String,
        required: true
    },
    analysisResult: {
        skinTone: String, // e.g., "Fair", "Medium", "Tan"
        undertone: String, // "Cool", "Warm", "Neutral"
        concerns: [String], // ["Acne", "Dark Circles", "Oiliness"]
        skinType: String, // "Oily", "Dry", "Combination", "Normal"
        confidence: Number
    },
    recommendedRoutine: [{
        step: String, // "Toner", "Serum", "Cream"
        product: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'Product'
        },
        productName: String,
        usage: String
    }],
    createdAt: {
        type: Date,
        default: Date.now
    }
});

module.exports = mongoose.model('AiAnalysis', AiAnalysisSchema);
