const mongoose = require('mongoose');

const campaignSchema = new mongoose.Schema({
    title: { type: String, required: true },
    endDate: { type: Date, required: true },
    status: { type: String, enum: ['LIVE', 'DRAFT', 'ENDED'], default: 'DRAFT' },
    targetRevenue: { type: Number, required: true },
    currentRevenue: { type: Number, default: 0 }
}, { timestamps: true });

module.exports = mongoose.model('Campaign', campaignSchema);
