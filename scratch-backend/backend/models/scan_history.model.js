const mongoose = require('mongoose');

const ScanHistorySchema = new mongoose.Schema({
    userId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        index: true
    },
    barcodeValue: {
        type: String,
        required: true,
        trim: true
    },
    pointsEarned: {
        type: Number,
        default: 50
    }
}, { timestamps: true });

module.exports = mongoose.model('ScanHistory', ScanHistorySchema);
