const express = require('express');
const router = express.Router();
const { register, login, logout, getMe, refreshToken, forgotPassword } = require('../controllers/authController');
const { protect } = require('../middleware/authMiddleware');

router.post('/register', register);
router.post('/login', login);
router.post('/refresh', refreshToken);
router.post('/forgot-password', forgotPassword);
router.post('/logout', protect, logout);
router.get('/me', protect, getMe);

module.exports = router;
