const mongoose = require('mongoose');

const VoucherSchema = new mongoose.Schema({
    code: {
        type: String,
        required: true,
        trim: true,
        index: true
    },
    userId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true
    },
    discountPct: {
        type: Number,
        required: true,
        min: 1,
        max: 100
    },
    validTo: {
        type: Date,
        required: true
    },
    isUsed: {
        type: Boolean,
        default: false
    },
    source: {
        type: String,
        enum: ['Admin', 'LoyaltyRedeem'],
        required: true
    }
}, { timestamps: true });

module.exports = mongoose.models.Voucher || mongoose.model('Voucher', VoucherSchema);
