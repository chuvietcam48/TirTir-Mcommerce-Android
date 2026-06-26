const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const User = require('../models/User');
const sendEmail = require('../utils/sendEmail');

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

  const user = await User.create({ firstName, lastName, email, password });

  try {
    const admin = require('firebase-admin');
    const uid = user._id.toString();
    await admin.firestore().collection('users').doc(uid).set({
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      role: user.role || 'user',
      skinType: user.skinType || 'unknown',
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
  } catch (error) {
    console.error("Lỗi đồng bộ Firestore khi đăng ký:", error);
  }

  const token = signAccessToken(user._id, user.role);
  const refreshToken = signRefreshToken(user._id);

  user.refreshTokenHash = hashToken(refreshToken);
  await user.save({ validateBeforeSave: false });

  res.status(201).json({ 
    success: true, 
    message: 'Đăng ký thành công.', 
    token, 
    refreshToken, 
    user: user.toClientJSON() 
  });
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

  const newToken = signAccessToken(user._id, user.role);
  const newRefreshToken = signRefreshToken(user._id);

  user.refreshTokenHash = hashToken(newRefreshToken);
  await user.save({ validateBeforeSave: false });

  res.status(200).json({ 
    success: true, 
    token: newToken,
    refreshToken: newRefreshToken
  });
};

// POST /api/v1/auth/forgot-password
exports.forgotPassword = async (req, res) => {
  const { email } = req.body;
  if (!email) {
    return res.status(400).json({ success: false, message: 'Vui lòng cung cấp email.' });
  }

  const user = await User.findOne({ email: email.toLowerCase() });
  if (!user) {
    return res.status(200).json({ success: true, message: 'Nếu email tồn tại, hệ thống đã gửi mã xác nhận.' });
  }

  try {
    const otp = Math.floor(1000 + Math.random() * 9000).toString();
    user.resetPasswordOTP = await bcrypt.hash(otp, 12);
    user.resetPasswordExpires = Date.now() + 10 * 60 * 1000; // 10 minutes
    await user.save({ validateBeforeSave: false });

    const message = `Mã xác nhận (OTP) để đặt lại mật khẩu của bạn là: ${otp}\n\nMã này sẽ hết hạn trong vòng 10 phút.`;
    await sendEmail({
      email: user.email,
      subject: 'Mã xác nhận đặt lại mật khẩu - TirTir',
      message
    });

    res.status(200).json({ success: true, message: 'Mã xác nhận đã được gửi đến email của bạn.' });
  } catch (error) {
    console.error('Lỗi gửi email OTP:', error);
    user.resetPasswordOTP = undefined;
    user.resetPasswordExpires = undefined;
    await user.save({ validateBeforeSave: false });
    res.status(500).json({ success: false, message: 'Lỗi server khi gửi email.' });
  }
};

// POST /api/v1/auth/verify-otp
exports.verifyOTP = async (req, res) => {
  const { email, otp } = req.body;
  if (!email || !otp) {
    return res.status(400).json({ success: false, message: 'Vui lòng cung cấp email và mã OTP.' });
  }

  const user = await User.findOne({ 
    email: email.toLowerCase(),
    resetPasswordExpires: { $gt: Date.now() }
  }).select('+resetPasswordOTP');

  if (!user || !user.resetPasswordOTP) {
    return res.status(400).json({ success: false, message: 'Mã OTP không hợp lệ hoặc đã hết hạn.' });
  }

  const isMatch = await bcrypt.compare(otp, user.resetPasswordOTP);
  if (!isMatch) {
    return res.status(400).json({ success: false, message: 'Mã OTP không chính xác.' });
  }

  // OTP hop le, tao resetToken
  const resetToken = jwt.sign({ id: user._id }, process.env.JWT_SECRET, { expiresIn: '10m' });
  
  user.resetPasswordOTP = undefined;
  await user.save({ validateBeforeSave: false });

  res.status(200).json({ success: true, message: 'Xác thực OTP thành công.', data: resetToken });
};

// POST /api/v1/auth/reset-password
exports.resetPassword = async (req, res) => {
  const { email, resetToken, newPassword } = req.body;
  if (!email || !resetToken || !newPassword) {
    return res.status(400).json({ success: false, message: 'Vui lòng cung cấp đầy đủ thông tin.' });
  }

  let decoded;
  try {
    decoded = jwt.verify(resetToken, process.env.JWT_SECRET);
  } catch (err) {
    return res.status(401).json({ success: false, message: 'Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.' });
  }

  const user = await User.findOne({ _id: decoded.id, email: email.toLowerCase() }).select('+resetPasswordExpires');
  if (!user) {
    return res.status(400).json({ success: false, message: 'Tài khoản không tồn tại.' });
  }

  if (newPassword.length < 8) {
    return res.status(400).json({ success: false, message: 'Mật khẩu phải có ít nhất 8 ký tự.' });
  }

  user.password = newPassword;
  user.resetPasswordExpires = undefined;
  await user.save();

  res.status(200).json({ success: true, message: 'Đặt lại mật khẩu thành công. Bạn có thể đăng nhập.' });
};
