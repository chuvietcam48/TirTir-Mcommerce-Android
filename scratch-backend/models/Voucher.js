const mongoose = require('mongoose');

const voucherSchema = new mongoose.Schema({
  code: { type: String, required: true, unique: true, uppercase: true, trim: true, index: true },
  voucherCode: { type: String, uppercase: true, trim: true }, // backward compatibility
  userId: { type: mongoose.Schema.Types.Mixed, required: true, index: true }, // accepts ObjectId or String uid
  discountPct: { type: Number, required: true, min: 1, max: 100 },
  reason: { type: String, default: 'PUBLIC_ROUTINE_REWARD' },
  source: { type: String, default: 'routine' },
  status: { type: String, enum: ['active', 'used', 'expired'], default: 'active' },
  description: { type: String, default: 'Discount Voucher' },
  expiryDate: { type: Date, required: true },
  isUsed: { type: Boolean, default: false },
  usedAt: { type: Date, default: null }
}, { timestamps: true });

voucherSchema.pre('save', function(next) {
  if (this.code && !this.voucherCode) {
    this.voucherCode = this.code;
  } else if (this.voucherCode && !this.code) {
    this.code = this.voucherCode;
  }
  next();
});

module.exports = mongoose.model('Voucher', voucherSchema);

