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
  },
  { timestamps: true }
);

userSchema.pre('save', async function (next) {
  if (!this.isModified('password')) return next();
  this.password = await bcrypt.hash(this.password, 12);
  next();
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
    name: `${this.firstName} ${this.lastName}`.trim(),
    email: this.email,
    phone: this.phone,
    avatar: this.avatar,
    role: this.role,
    isEmailVerified: this.isEmailVerified,
    gender: this.gender,
    birthDate: this.birthDate,
    addresses: mappedAddresses,
  };
};

module.exports = mongoose.model('User', userSchema);
