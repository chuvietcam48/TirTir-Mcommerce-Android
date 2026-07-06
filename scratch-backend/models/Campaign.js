const mongoose = require('mongoose');

const campaignSchema = new mongoose.Schema({
    title: { type: String, required: true },
    message: { type: String },
    path: { type: String },
    targetAudience: { type: String, default: 'All users' },
    startDate: { type: Date },
    endDate: { type: Date, required: true },
    status: { type: String, enum: ['LIVE', 'SCHEDULED', 'DRAFT', 'ENDED'], default: 'DRAFT' },
    targetRevenue: { type: Number, default: 0 },
    currentRevenue: { type: Number, default: 0 }
}, { timestamps: true });

module.exports = mongoose.models.Campaign || mongoose.model('Campaign', campaignSchema);
