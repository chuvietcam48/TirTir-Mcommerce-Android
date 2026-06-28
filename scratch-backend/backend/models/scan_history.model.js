const mongoose = require('mongoose');

const ScanHistorySchema = new mongoose.Schema({
    userId: {
        type: mongoose.Schema.Types.Mixed,
        required: true,
        index: true
    },
    barcodeValue: {
        type: String,
        required: true,
        trim: true,
        index: true
    },
    monthKey: {
        type: String,
        index: true
    },
    pointsEarned: {
        type: Number,
        default: 50
    }
}, { timestamps: true });

ScanHistorySchema.index({ userId: 1, monthKey: 1 });

module.exports = mongoose.model('ScanHistory', ScanHistorySchema);

