const mongoose = require('mongoose');

const voucherSchema = new mongoose.Schema({
  voucherCode: { type: String, required: true, unique: true, uppercase: true, trim: true },
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  discountPct: { type: Number, required: true, min: 1, max: 100 },
  description: { type: String, default: 'Discount Voucher' },
  expiryDate: { type: Date, required: true },
  isUsed: { type: Boolean, default: false },
  usedAt: { type: Date }
}, { timestamps: true });

module.exports = mongoose.model('Voucher', voucherSchema);
