const mongoose = require('mongoose');

const CampaignSchema = new mongoose.Schema({
    title: { type: String, required: true },
    status: { type: String, enum: ['Active', 'Paused', 'Completed', 'Draft'], default: 'Draft' },
    currentRevenue: { type: Number, default: 0 },
    targetRevenue: { type: Number, default: 0 },
    startDate: { type: Date, default: Date.now },
    endDate: { type: Date, required: true },
    description: String,
    type: { type: String, enum: ['Flash Sale', 'Discount', 'Product Launch', 'Holiday'], default: 'Discount' }
}, { timestamps: true });

module.exports = mongoose.models.Campaign || mongoose.model('Campaign', CampaignSchema);
