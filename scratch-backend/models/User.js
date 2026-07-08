const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const addressSchema = new mongoose.Schema({
  label: { type: String, default: 'Nhà' },
  recipientName: { type: String, required: true },
  phone: { type: String, required: true },
  street: { type: String, required: true },
  ward: String,
  district: { type: String, required: true },
  province: { type: String, required: true },
  isDefault: { type: Boolean, default: false },
});

const userSchema = new mongoose.Schema(
  {
    firstName: { type: String, required: true, trim: true },
    lastName: { type: String, required: true, trim: true },
    email: {
      type: String,
      required: true,
      unique: true,
      lowercase: true,
      trim: true,
      match: [/^[\w-.]+@[\w-]+\.[\w-.]+$/, 'Email không hợp lệ'],
    },
    password: { type: String, required: true, select: false },
    phone: { type: String, default: null },
    avatar: { type: String, default: null },
    role: {
      type: String,
      enum: ['user', 'admin', 'inventory_staff', 'customer_service'],
      default: 'user',
    },
    isEmailVerified: { type: Boolean, default: false },
    gender: { type: String, default: null },
    birthDate: { type: String, default: null },
    addresses: [addressSchema],
    refreshTokenHash: { type: String, select: false, default: null },
    isActive: { type: Boolean, default: true },
    resetPasswordOTP: { type: String, select: false },
    resetPasswordExpires: { type: Date, select: false },
    firebaseUid: { type: String, default: null },
    fcmTokens: [{
      token: String,
      platform: String,
      firebaseUid: String,
      deviceModel: String,
      appVersion: String,
      active: { type: Boolean, default: true },
      lastSeenAt: Date,
      createdAt: { type: Date, default: Date.now }
    }],
    // ===== SKIN PROFILE (Latest AI Analysis Snapshot) =====
    skinProfile: {
        skinTone:       { type: String, default: null },
        undertone:      { type: String, default: null },
        skinHex:        { type: String, default: null },
        ITA_category:   { type: String, default: null },
        texture:        { type: String, default: null },
        pores:          { type: String, default: null },
        hydration:      { type: String, default: null },
        skinType:       { type: String, enum: ['Dry', 'Oily', 'Combination', 'Normal', 'Sensitive'], default: null },
        concerns:       { type: [String], default: [] },
        recommendations:{ type: [String], default: [] },
        confidence:     { type: Number, default: null },
        lastAnalyzedAt: { type: Date, default: null }
    }
  },
  { timestamps: true }
);

userSchema.pre('save', async function () {
  if (!this.isModified('password')) return;
  this.password = await bcrypt.hash(this.password, 12);
});

userSchema.methods.comparePassword = async function (candidate) {
  return bcrypt.compare(candidate, this.password);
};

// Returns fields matching User.java SerializedName annotations exactly
userSchema.methods.toClientJSON = function () {
  const mappedAddresses = this.addresses.map(addr => ({
    _id: addr._id,
    fullName: addr.recipientName,
    phone: addr.phone,
    street: addr.street,
    ward: addr.ward,
    district: addr.district,
    city: addr.province,
    isDefault: addr.isDefault
  }));

  return {
    _id: this._id,
    name: (`${this.firstName === 'undefined' ? '' : (this.firstName || '')} ${this.lastName === 'undefined' ? '' : (this.lastName || '')}`.trim()) || (this.name === 'undefined' ? '' : (this.name || '')) || 'Người dùng TirTir',
    email: this.email,
    phone: this.phone,
    avatar: this.avatar,
    role: this.role,
    isEmailVerified: this.isEmailVerified,
    gender: this.gender,
    birthDate: this.birthDate,
    addresses: mappedAddresses,
    skinProfile: this.skinProfile,
  };
};

module.exports = mongoose.models.User || mongoose.model('User', userSchema);
