// backend/models/order.model.js
const mongoose = require('mongoose');
const { ORDER_STATUS, PAYMENT_METHOD, PAYMENT_STATUS } = require('../constants');

const OrderSchema = new mongoose.Schema({
    user: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true
    },
    items: [
        {
            product: {
                type: mongoose.Schema.Types.ObjectId,
                ref: 'Product',
                required: true
            },
            name: String,
            quantity: Number,
            price: Number,
            shade: String,
            image: String
        }
    ],
    shippingAddress: {
        fullName: { type: String, required: true },
        phone: { type: String, required: true },
        address: { type: String, required: true },
        ward: { type: String, default: '' },         // Phường / xã
        district: { type: String, default: '' },     // Quận / huyện (GHN to_district_name)
        city: { type: String, required: true }       // Tỉnh / thành phố (GHN to_province_name)
    },
    paymentMethod: {
        type: String,
        enum: ['MOMO', 'VNPAY', 'CARD'],
        required: true
    },
    status: {
        type: String,
        enum: Object.values(ORDER_STATUS),
        default: ORDER_STATUS.PENDING
    },
    // ─── GHN Shipping Integration ────────────────────────────────────────────
    trackingNumber: {
        type: String,
        default: null
    },
    expectedDeliveryDate: {
        type: Date,
        default: null
    },
    ghnOrderCode: {
        type: String,
        default: null  // Populated when Admin marks order as Shipped
    },
    ghnProcessedEvents: {
        type: [String],
        default: []    // Stores processed GHN event UUIDs for idempotency
    },
    orderStatus: {
        type: String,
        enum: ['PROCESSING', 'SHIPPING', 'DELIVERED', 'CANCELLED'],
        default: 'PROCESSING'
    },
    paymentTranId: { type: String }, // Lưu mã giao dịch của Momo/VNPay để dùng khi Refund
    paymentInfo: { // Lưu dữ liệu trả về từ VNPay/Momo để đối soát
        type: Object,
        default: {}
    },
    totalAmount: {
        type: Number,
        required: true
    },
    recoveredFrom: {
        type: String,
        enum: ['email_1', 'email_2', 'email_3', 'manual']
    },
    // ─── Fulfillment Info ────────────────────────────────────────────────────
    carrier:    { type: String, default: null },   // GHN, GHTK, J&T, etc.
    isPacked:   { type: Boolean, default: false },
    shippingCost: { type: Number, default: 0 },
    // ─── Status Audit Trail ───────────────────────────────────────────────────
    statusHistory: [
        {
            status:    { type: String, required: true },
            timestamp: { type: Date, default: Date.now },
            note:      { type: String, default: '' }
        }
    ]
},
    { timestamps: true });

module.exports = mongoose.model('Order', OrderSchema);