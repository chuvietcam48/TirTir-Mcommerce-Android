const mongoose = require('mongoose');

const couponSchema = new mongoose.Schema({
    code: {
        type: String,
        required: [true, 'Coupon code is required'],
        unique: true,
        uppercase: true,
        trim: true,
        index: true // Fast lookup
    },
    discountType: {
        type: String,
        required: [true, 'Discount type is required'],
        enum: {
            values: ['percentage', 'fixed'],
            message: 'Discount type must be either percentage or fixed'
        }
    },
    discountValue: {
        type: Number,
        required: [true, 'Discount value is required'],
        min: [0, 'Discount value cannot be negative']
    },
    minOrderValue: {
        type: Number,
        default: 0,
        min: [0, 'Minimum order value cannot be negative']
    },
    maxDiscount: {
        type: Number,
        min: [0, 'Maximum discount cannot be negative']
        // maxDiscount is OPTIONAL — no required-for-percentage constraint
    },
    validFrom: {
        type: Date,
        default: Date.now
    },
    validTo: {
        type: Date,
        required: [true, 'Expiry date is required']
    },
    usageLimit: {
        type: Number,
        default: 100,
        min: [1, 'Usage limit must be at least 1']
    },
    usedCount: {
        type: Number,
        default: 0,
        min: [0, 'Used count cannot be negative']
    },
    applicableProducts: [{
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Product'
    }],
    active: {
        type: Boolean,
        default: true
    }
}, {
    timestamps: true
});

// Index for efficient queries
// couponSchema.index({ code: 1 }); // Removed duplicate index
couponSchema.index({ active: 1, validTo: 1 });

// Virtual to check if coupon is currently valid
couponSchema.virtual('isValid').get(function () {
    const now = new Date();
    return this.active &&
        this.validFrom <= now &&
        this.validTo >= now &&
        this.usedCount < this.usageLimit;
});

// Pre-save validation (Mongoose 9: throw error instead of using next callback)
couponSchema.pre('save', function () {
    if (this.validFrom >= this.validTo) {
        throw new Error('validFrom must be before validTo');
    }
});

module.exports = mongoose.model('Coupon', couponSchema);
