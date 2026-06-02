const express = require('express');
const router = express.Router();

// Import controllers
const {
    register,
    login,
    getMe,
    verifyEmail,
    forgotPassword,
    resetPassword,
    logout,
    refreshToken
} = require('../controllers/auth.controller');

// Import middleware
const { protect, authorize } = require('../middlewares/auth');
const { authLimiter } = require('../middlewares/rateLimit');
const { registerValidator, loginValidator } = require('../validators/auth.validator');
const { changePasswordValidator } = require('../validators/user.validator');
const { validate } = require('../middlewares/validate');
const { changePassword } = require('../controllers/user.controller');

/**
 * ===== AUTHENTICATION ROUTES =====
 * Clean, RESTful API design for TirTir Cosmetics Platform
 */

// ===== PUBLIC ROUTES (No authentication required) =====

/**
 * @swagger
 * tags:
 *   name: Auth
 *   description: Đăng ký, đăng nhập, xác thực email và quản lý mật khẩu
 */

/**
 * @swagger
 * /auth/register:
 *   post:
 *     summary: Đăng ký tài khoản mới
 *     tags: [Auth]
 *     security: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [name, email, password]
 *             properties:
 *               name:
 *                 type: string
 *                 example: Nguyen Van A
 *               email:
 *                 type: string
 *                 format: email
 *                 example: user@tirtir.com
 *               password:
 *                 type: string
 *                 minLength: 6
 *                 example: secret123
 *     responses:
 *       201:
 *         description: Đăng ký thành công. Email xác thực đã được gửi.
 *       400:
 *         description: Dữ liệu không hợp lệ hoặc email đã tồn tại
 */
router.post('/register', authLimiter, registerValidator, validate, register);

/**
 * @swagger
 * /auth/login:
 *   post:
 *     summary: Đăng nhập
 *     tags: [Auth]
 *     security: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [email, password]
 *             properties:
 *               email:
 *                 type: string
 *                 format: email
 *                 example: user@tirtir.com
 *               password:
 *                 type: string
 *                 example: secret123
 *     responses:
 *       200:
 *         description: Đăng nhập thành công. Trả về JWT token.
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                 token:
 *                   type: string
 *       400:
 *         description: Dữ liệu không hợp lệ
 *       401:
 *         description: Sai email hoặc mật khẩu
 *       403:
 *         description: Tài khoản bị khóa
 */
router.post('/login', authLimiter, loginValidator, validate, login);

/**
 * @swagger
 * /auth/verify-email/{token}:
 *   get:
 *     summary: Xác thực email người dùng
 *     tags: [Auth]
 *     security: []
 *     parameters:
 *       - in: path
 *         name: token
 *         required: true
 *         schema:
 *           type: string
 *         description: Token xác thực được gửi qua email
 *     responses:
 *       302:
 *         description: Redirect về frontend sau khi xác thực thành công
 *       400:
 *         description: Token không hợp lệ hoặc đã hết hạn
 */
router.get('/verify-email/:token', verifyEmail);

/**
 * @swagger
 * /auth/forgot-password:
 *   post:
 *     summary: Gửi email đặt lại mật khẩu
 *     tags: [Auth]
 *     security: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [email]
 *             properties:
 *               email:
 *                 type: string
 *                 format: email
 *                 example: user@tirtir.com
 *     responses:
 *       200:
 *         description: Email đặt lại mật khẩu đã được gửi
 *       404:
 *         description: Không tìm thấy tài khoản với email này
 */
router.post('/forgot-password', forgotPassword);

/**
 * @swagger
 * /auth/reset-password/{resettoken}:
 *   put:
 *     summary: Đặt lại mật khẩu với token
 *     tags: [Auth]
 *     security: []
 *     parameters:
 *       - in: path
 *         name: resettoken
 *         required: true
 *         schema:
 *           type: string
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [password]
 *             properties:
 *               password:
 *                 type: string
 *                 minLength: 6
 *                 example: newpassword123
 *     responses:
 *       200:
 *         description: Đặt lại mật khẩu thành công
 *       400:
 *         description: Token không hợp lệ hoặc đã hết hạn
 */
router.put('/reset-password/:resettoken', resetPassword);

// ===== PROTECTED ROUTES (Require authentication) =====

/**
 * @swagger
 * /auth/me:
 *   get:
 *     summary: Lấy thông tin người dùng hiện tại
 *     tags: [Auth]
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: Thông tin người dùng
 *       401:
 *         description: Chưa đăng nhập
 */
router.get('/me', protect, getMe);

/**
 * @swagger
 * /auth/logout:
 *   post:
 *     summary: Đăng xuất — invalidates refresh token in DB and clears cookie
 *     tags: [Auth]
 *     security:
 *       - bearerAuth: []
 *     responses:
 *       200:
 *         description: Đăng xuất thành công
 */
router.post('/logout', protect, logout);

/**
 * Change password for authenticated user
 * Mirrors /api/v1/users/change-password for FE consistency
 */
router.post('/change-password', protect, changePasswordValidator, validate, changePassword);

// ===== FUTURE IMPLEMENTATION =====

/**
 * @swagger
 * /auth/refresh-token:
 *   post:
 *     summary: Làm mới JWT token (Placeholder)
 *     tags: [Auth]
 *     security: []
 *     responses:
 *       501:
 *         description: Chưa triển khai
 */
router.post('/refresh-token', refreshToken);

module.exports = router;
