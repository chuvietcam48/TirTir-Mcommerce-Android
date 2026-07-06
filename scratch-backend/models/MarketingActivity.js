const mongoose = require('mongoose');

const marketingActivitySchema = new mongoose.Schema({
    type: { type: String, enum: ['success', 'system', 'draft'], required: true },
    title: { type: String, required: true },
    targetOrStatus: { type: String, required: true }
}, { timestamps: true });

module.exports = mongoose.models.MarketingActivity || mongoose.model('MarketingActivity', marketingActivitySchema);
