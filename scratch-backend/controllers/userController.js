const User = require('../models/User');

// Helper: build the standard client JSON matching User.java @SerializedName fields
const toClient = (user) => user.toClientJSON ? user.toClientJSON() : user;

// ─────────────────────────────────────
// GET /api/v1/users/profile  (protected)
// Response: ApiResponse<User> { success, data }
// ─────────────────────────────────────
exports.getProfile = async (req, res) => {
  const user = await User.findById(req.user.id);
  if (!user) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
  }
  res.status(200).json({ success: true, data: toClient(user) });
};

// ─────────────────────────────────────
// PUT /api/v1/users/profile  (protected)
// Body: { name?, phone?, gender?, birthDate?, avatar? }
// Response: ApiResponse<User> { success, data }
// ─────────────────────────────────────
exports.updateProfile = async (req, res) => {
  const allowed = ['phone', 'gender', 'birthDate', 'avatar'];
  const update = {};

  // Handle "name" → split into firstName + lastName
  if (req.body.name) {
    const parts = req.body.name.trim().split(/\s+/);
    if (parts.length === 1) {
      update.firstName = parts[0];
      update.lastName = '';
    } else {
      update.firstName = parts[parts.length - 1];
      update.lastName = parts.slice(0, parts.length - 1).join(' ');
    }
  }

  // Copy other allowed fields
  allowed.forEach((field) => {
    if (req.body[field] !== undefined) update[field] = req.body[field];
  });

  if (Object.keys(update).length === 0) {
    return res.status(400).json({ success: false, message: 'Không có trường nào để cập nhật.' });
  }

  const user = await User.findByIdAndUpdate(req.user.id, update, {
    returnDocument: 'after',
    runValidators: true,
  });

  if (!user) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
  }

  res.status(200).json({ success: true, data: toClient(user) });
};

// ─────────────────────────────────────
// PUT /api/v1/users/skin-profile (protected)
// Body: { skinTone, undertone, skinHex, ITA_category, texture, pores, hydration, skinType, concerns, recommendations, confidence }
// ─────────────────────────────────────
exports.updateSkinProfile = async (req, res) => {
  try {
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
    }

    const {
      skinTone, undertone, skinHex, ITA_category, texture, pores, hydration,
      skinType, concerns, recommendations, confidence
    } = req.body;

    user.skinProfile = {
      skinTone,
      undertone,
      skinHex,
      ITA_category,
      texture,
      pores,
      hydration,
      skinType,
      concerns: Array.isArray(concerns) ? concerns : [],
      recommendations: Array.isArray(recommendations) ? recommendations : [],
      confidence,
      lastAnalyzedAt: new Date()
    };

    await user.save();
    res.status(200).json({ success: true, data: toClient(user) });
  } catch (error) {
    console.error('Update Skin Profile error:', error);
    res.status(500).json({ success: false, message: 'Lỗi khi lưu kết quả scan.' });
  }
};

// ─────────────────────────────────────
// GET /api/v1/users/addresses  (protected)
// Response: ApiResponse<List<Address>> { success, data }
// Address fields (matching Address.java @SerializedName):
//   _id, fullName, phone, street, ward, district, city, isDefault
// ─────────────────────────────────────
exports.getAddresses = async (req, res) => {
  const user = await User.findById(req.user.id);
  if (!user) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
  }
  // Map internal schema fields to FE-expected fields
  const addresses = user.addresses.map(mapAddressToClient);
  res.status(200).json({ success: true, data: addresses });
};

// ─────────────────────────────────────
// POST /api/v1/users/addresses  (protected)
// Body: { fullName, phone, street, ward?, district, city, isDefault? }
// Response: ApiResponse<User> { success, data } — full updated user
// ─────────────────────────────────────
exports.addAddress = async (req, res) => {
  const { fullName, phone, street, ward, district, city, isDefault } = req.body;

  if (!fullName || !phone || !street || !district || !city) {
    return res.status(400).json({
      success: false,
      message: 'Vui lòng điền đầy đủ: fullName, phone, street, district, city.',
    });
  }

  const user = await User.findById(req.user.id);
  if (!user) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
  }

  // If new address is default → unset all others
  if (isDefault) {
    user.addresses.forEach((a) => { a.isDefault = false; });
  }

  // Store using internal schema field names (recipientName, province)
  user.addresses.push({
    recipientName: fullName,
    phone,
    street,
    ward: ward || '',
    district,
    province: city,
    isDefault: isDefault || user.addresses.length === 0, // first address → auto-default
  });

  await user.save({ validateBeforeSave: true });
  res.status(201).json({ success: true, data: toClient(user) });
};

// ─────────────────────────────────────
// PUT /api/v1/users/addresses/:id  (protected)
// Body: { fullName?, phone?, street?, ward?, district?, city?, isDefault? }
// Response: ApiResponse<User> { success, data }
// ─────────────────────────────────────
exports.updateAddress = async (req, res) => {
  const user = await User.findById(req.user.id);
  if (!user) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
  }

  const addr = user.addresses.id(req.params.id);
  if (!addr) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy địa chỉ.' });
  }

  const { fullName, phone, street, ward, district, city, isDefault } = req.body;
  if (fullName)   addr.recipientName = fullName;
  if (phone)      addr.phone         = phone;
  if (street)     addr.street        = street;
  if (ward)       addr.ward          = ward;
  if (district)   addr.district      = district;
  if (city)       addr.province      = city;

  if (isDefault === true) {
    user.addresses.forEach((a) => { a.isDefault = false; });
    addr.isDefault = true;
  }

  await user.save({ validateBeforeSave: true });
  res.status(200).json({ success: true, data: toClient(user) });
};

// ─────────────────────────────────────
// DELETE /api/v1/users/addresses/:id  (protected)
// Response: ApiResponse<Void> { success, message }
// ─────────────────────────────────────
exports.deleteAddress = async (req, res) => {
  const user = await User.findById(req.user.id);
  if (!user) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
  }

  const addr = user.addresses.id(req.params.id);
  if (!addr) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy địa chỉ.' });
  }

  const wasDefault = addr.isDefault;
  addr.deleteOne();

  // If deleted address was default → auto-promote first remaining address
  if (wasDefault && user.addresses.length > 0) {
    user.addresses[0].isDefault = true;
  }

  await user.save({ validateBeforeSave: false });
  res.status(200).json({ success: true, message: 'Đã xóa địa chỉ.' });
};

// ─────────────────────────────────────
// PATCH /api/v1/users/addresses/:id/set-default  (protected)
// Response: ApiResponse<User> { success, data }
// ─────────────────────────────────────
exports.setDefaultAddress = async (req, res) => {
  const user = await User.findById(req.user.id);
  if (!user) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
  }

  const addr = user.addresses.id(req.params.id);
  if (!addr) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy địa chỉ.' });
  }

  // Unset all then set chosen one
  user.addresses.forEach((a) => { a.isDefault = false; });
  addr.isDefault = true;

  await user.save({ validateBeforeSave: false });
  res.status(200).json({ success: true, data: toClient(user) });
};

// ─────────────────────────────────────
// HELPER: Map internal MongoDB address doc → FE Address.java field names
// Mongoose schema:  recipientName, province
// Address.java:     fullName,      city
// ─────────────────────────────────────
function mapAddressToClient(addr) {
  return {
    _id:       addr._id,
    fullName:  addr.recipientName,   // @SerializedName("fullName")
    phone:     addr.phone,
    street:    addr.street,
    ward:      addr.ward,
    district:  addr.district,
    city:      addr.province,        // @SerializedName("city")
    isDefault: addr.isDefault,
  };
}
