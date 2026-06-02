const mongoose = require('mongoose');

const StockHistorySchema = new mongoose.Schema({
    product: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Product',
        required: true
    },
    // Enhanced Audit Fields
    action: {
        type: String,
        required: true,
        enum: ['Import', 'Export', 'Refund', 'Adjust', 'Sale', 'Reserve', 'Release']
    },
    change_type: {
        type: String,
        required: true,
        enum: ['Increase', 'Decrease']
    },
    source_id: { // Can be Order ID or Import Batch ID
        type: String,
        index: true
    },
    balance_before: {
        type: Number,
        required: true
    },
    balance_after: {
        type: Number,
        required: true
    },
    changeAmount: {
        type: Number,
        required: true
    },
    // Legacy fields kept for compatibility or detailed description
    reason: {
        type: String,
        default: 'Other'
    },
    performedBy: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User'
    }
}, {
    timestamps: true
});

module.exports = mongoose.model('StockHistory', StockHistorySchema);
