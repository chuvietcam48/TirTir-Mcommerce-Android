const mongoose = require('mongoose');

const SettingSchema = new mongoose.Schema({
    bannerUrl: {
        type: String,
        default: ''
    },
    shippingFee: {
        type: Number,
        default: 0
    },
    freeShippingThreshold: {
        type: Number,
        default: 0
    },
    contactPhone: {
        type: String,
        default: ''
    },
    contactEmail: {
        type: String,
        default: ''
    },
    socialLinks: {
        facebook: { type: String, default: '' },
        instagram: { type: String, default: '' },
        tiktok: { type: String, default: '' }
    },
    bankInfo: {
        bankName: { type: String, default: '' },
        accountNumber: { type: String, default: '' },
        accountHolder: { type: String, default: '' }
    }
}, {
    timestamps: true
});

module.exports = mongoose.model('Setting', SettingSchema);
