const mongoose = require('mongoose');

/**
 * RoutineFeedback — records user feedback on AI-recommended routine steps.
 * Fed back into Gemini prompt for personalized improvements over time.
 */
const RoutineFeedbackSchema = new mongoose.Schema({
    user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },

    // Skin profile at the time of recommendation
    skinProfile: {
        skinType:  String,
        skinTone:  String,
        undertone: String,
        concerns:  [String]
    },

    // The step being rated
    step: { type: String, required: true },            // e.g. "Cleanser", "Serum"
    productId: { type: String, required: true },       // Product_ID
    productName: String,

    // User feedback
    rating: { type: Number, required: true, min: 1, max: 5 },  // 1-5 stars
    purchased: { type: Boolean, default: false },               // Did user actually buy it?
    feedback: { type: String, maxLength: 500 },                 // Optional text feedback

    // Action taken
    action: {
        type: String,
        enum: ['kept', 'skipped', 'added_to_cart', 'purchased'],
        default: 'kept'
    }
}, { collection: 'routine_feedbacks', timestamps: true });

// Index for querying feedback by product (useful for aggregate scoring)
RoutineFeedbackSchema.index({ productId: 1 });
// Index for querying user's feedback history
RoutineFeedbackSchema.index({ user: 1, createdAt: -1 });

module.exports = mongoose.model('RoutineFeedback', RoutineFeedbackSchema);
