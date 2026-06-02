const mongoose = require('mongoose');

const notificationSchema = new mongoose.Schema({
    user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    type: { type: String, enum: ['order', 'promotion', 'system'], required: true },
    title: { type: String, required: true },
    message: { type: String, required: true },
    link: { type: String }, // e.g., "/orders/123"
    isRead: { type: Boolean, default: false },
    image: { type: String }, // Optional: Product image or Icon
}, { timestamps: true });

module.exports = mongoose.model('Notification', notificationSchema);
