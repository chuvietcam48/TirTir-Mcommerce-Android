const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const User = require('../models/User');

const signAccessToken = (userId, role) => {
  const expiresIn =
    role === 'admin' || role === 'inventory_staff' || role === 'customer_service'
      ? process.env.JWT_ADMIN_EXPIRES_IN || '8h'
      : process.env.JWT_EXPIRES_IN || '30d';
  return jwt.sign({ id: userId, role }, process.env.JWT_SECRET, { expiresIn });
};

const signRefreshToken = (userId) =>
  jwt.sign({ id: userId }, process.env.JWT_REFRESH_SECRET, {
    expiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '30d',
  });

const hashToken = (token) =>
  crypto.createHash('sha256').update(token).digest('hex');

// POST /api/v1/auth/register
// Body: { firstName, lastName, email, password }
// Response: ApiResponse<Void> { success, message }
exports.register = async (req, res) => {
  const { firstName, lastName, email, password } = req.body;

  if (!firstName || !lastName || !email || !password) {
    return res.status(400).json({ success: false, message: 'Vui lòng điền đầy đủ thông tin.' });
  }
  if (password.length < 8) {
    return res.status(400).json({ success: false, message: 'Mật khẩu phải có ít nhất 8 ký tự.' });
  }

  const existing = await User.findOne({ email: email.toLowerCase() });
  if (existing) {
    return res.status(409).json({ success: false, message: 'Email đã được sử dụng.' });
  }

  await User.create({ firstName, lastName, email, password });
  res.status(201).json({ success: true, message: 'Đăng ký thành công. Vui lòng đăng nhập.' });
};

// POST /api/v1/auth/login
// Body: { email, password }
// Response: LoginResponse.java { success, token, refreshToken, user }
exports.login = async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ success: false, message: 'Vui lòng nhập email và mật khẩu.' });
  }

  const user = await User.findOne({ email: email.toLowerCase() }).select('+password +refreshTokenHash');
  if (!user) {
    return res.status(401).json({ success: false, message: 'Email hoặc mật khẩu không đúng.' });
  }
  // HTTP 403 → AuthRepository maps to "Tài khoản đã bị khóa"
  if (!user.isActive) {
    return res.status(403).json({ success: false, message: 'Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ.' });
  }

  const passwordMatch = await user.comparePassword(password);
  if (!passwordMatch) {
    return res.status(401).json({ success: false, message: 'Email hoặc mật khẩu không đúng.' });
  }

  const token = signAccessToken(user._id, user.role);
  const refreshToken = signRefreshToken(user._id);

  user.refreshTokenHash = hashToken(refreshToken);
  await user.save({ validateBeforeSave: false });

  res.status(200).json({ success: true, token, refreshToken, user: user.toClientJSON() });
};

// POST /api/v1/auth/logout  (requires Bearer token)
// Response: ApiResponse<Void> { success, message }
exports.logout = async (req, res) => {
  await User.findByIdAndUpdate(req.user.id, { refreshTokenHash: null });
  res.status(200).json({ success: true, message: 'Đăng xuất thành công.' });
};

// GET /api/v1/auth/me  (requires Bearer token)
// Response: ApiResponse<User> { success, data }
exports.getMe = async (req, res) => {
  const user = await User.findById(req.user.id);
  if (!user) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản.' });
  }
  res.status(200).json({ success: true, data: user.toClientJSON() });
};

// POST /api/v1/auth/refresh
// Body: { refreshToken }
// Phase 1: Android client chưa dùng — endpoint sẵn sàng cho Phase 2
exports.refreshToken = async (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) {
    return res.status(400).json({ success: false, message: 'Refresh token không tồn tại.' });
  }

  let decoded;
  try {
    decoded = jwt.verify(refreshToken, process.env.JWT_REFRESH_SECRET);
  } catch {
    return res.status(401).json({ success: false, message: 'Refresh token không hợp lệ hoặc đã hết hạn.' });
  }

  const user = await User.findById(decoded.id).select('+refreshTokenHash');
  if (!user || user.refreshTokenHash !== hashToken(refreshToken)) {
    return res.status(401).json({ success: false, message: 'Refresh token đã bị thu hồi.' });
  }

  res.status(200).json({ success: true, token: signAccessToken(user._id, user.role) });
};
