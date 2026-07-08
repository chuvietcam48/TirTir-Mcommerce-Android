const mongoose = require('mongoose');

const MarketingActivitySchema = new mongoose.Schema({
    title: { type: String, required: true },
    targetOrStatus: String,
    type: { type: String, enum: ['system', 'user', 'campaign', 'promotion'], default: 'system' },
    status: { type: String, default: 'Success' }
}, { timestamps: true });

module.exports = mongoose.models.MarketingActivity || mongoose.model('MarketingActivity', MarketingActivitySchema);
