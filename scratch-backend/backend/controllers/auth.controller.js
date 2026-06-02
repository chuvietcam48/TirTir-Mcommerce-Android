const User = require('../models/user.model');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const sendEmail = require('../utils/sendEmail');
const { logActivity } = require('../utils/activityLogger');
const { createNotification } = require('./notification.controller');

/**
 * ===== AUTHENTICATION CONTROLLER =====
 * Handles user registration, login, email verification, and password reset
 * Built with robust error handling and graceful degradation
 */

// ===== HELPER: Get JWT Secret =====
const getJwtSecret = () => {
    const secret = process.env.JWT_SECRET;
    if (!secret) throw new Error('FATAL: JWT_SECRET is not defined in environment variables');
    return secret;
};

// ===== HELPER: Generate Short-lived Access Token (15m default) =====
const generateAccessToken = (userId, userRole) => {
    const expiresIn = process.env.JWT_EXPIRE || '15m';
    return jwt.sign(
        { id: userId, role: userRole },
        getJwtSecret(),
        { expiresIn }
    );
};

// ===== HELPER: Generate Opaque Refresh Token & Save Hash to DB =====
// Returns the plain refresh token string (sent to client)
const generateAndSaveRefreshToken = async (user) => {
    const plainToken = crypto.randomBytes(40).toString('hex');
    const hashed = crypto.createHash('sha256').update(plainToken).digest('hex');
    const expireDays = parseInt(process.env.JWT_REFRESH_EXPIRE_DAYS || '30', 10);
    user.refreshToken = hashed;
    user.refreshTokenExpire = new Date(Date.now() + expireDays * 24 * 60 * 60 * 1000);
    await user.save({ validateBeforeSave: false });
    return plainToken;
};

/**
 * @route   POST /api/v1/auth/register
 * @desc    Register new user with email verification
 * @access  Public
 */
exports.register = async (req, res) => {
    try {
        const { firstName, lastName, email, password } = req.body;

        // ===== VALIDATION =====
        if (!firstName || !lastName || !email || !password) {
            return res.status(400).json({
                success: false,
                message: 'Please provide first name, last name, email, and password'
            });
        }

        // Email format validation
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(email)) {
            return res.status(400).json({
                success: false,
                message: 'Please provide a valid email address'
            });
        }

        // Password strength validation (NIST SP 800-63B: minimum 8 characters)
        if (password.length < 8) {
            return res.status(400).json({
                success: false,
                message: 'Password must be at least 8 characters long'
            });
        }

        // ===== CHECK IF USER EXISTS =====
        const existingUser = await User.findOne({ email });
        if (existingUser) {
            return res.status(400).json({
                success: false,
                message: 'User with this email already exists'
            });
        }

        // ===== CHECK DEVELOPMENT MODE =====
        const isDevelopment = process.env.NODE_ENV === 'development';

        // Combine into a single display name for the User schema
        const name = `${firstName.trim()} ${lastName.trim()}`;

        // ===== CREATE USER =====
        const newUser = new User({
            name,
            email,
            password, // Will be hashed by pre-save hook
            isEmailVerified: isDevelopment // Auto-verify in dev mode
        });

        // Generate verification token (skip in dev mode)
        let verificationToken = null;
        if (!isDevelopment) {
            verificationToken = newUser.getEmailVerificationToken();
        }

        // Save user to database
        await newUser.save();

        console.log(`✅ User created: ${email} (ID: ${newUser._id})`);

        // Log Activity
        logActivity(req, 'AUTH', 'REGISTER', `New user registered: ${email}`, { userId: newUser._id });

        // Send Welcome Notification
        await createNotification(
            newUser._id,
            'system',
            'Welcome to TirTir!',
            'Thanks for joining. Here is a 10% discount code for your first order: WELCOME10',
            '/coupons'
        );

        // ===== DEVELOPMENT MODE: Skip Email — auto-login =====
        if (isDevelopment) {
            console.log('🔥 DEV MODE: Email verification bypassed');

            const accessToken = generateAccessToken(newUser._id, newUser.role);
            const refreshToken = await generateAndSaveRefreshToken(newUser);

            // Set refresh token as HTTP-only cookie (mirrors login behaviour)
            const expireDays = parseInt(process.env.JWT_REFRESH_EXPIRE_DAYS || '30', 10);
            res.cookie('refreshToken', refreshToken, {
                httpOnly: true,
                secure: false,
                sameSite: 'lax',
                maxAge: expireDays * 24 * 60 * 60 * 1000
            });

            return res.status(201).json({
                success: true,
                message: 'Registration successful! Welcome to TirTir.',
                token: accessToken,
                refreshToken,
                user: {
                    id: newUser._id,
                    name: newUser.name,
                    firstName,
                    lastName,
                    email: newUser.email,
                    role: newUser.role,
                    isEmailVerified: newUser.isEmailVerified
                }
            });
        }

        // ===== PRODUCTION MODE: Send Verification Email =====
        const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:4200';
        const verificationUrl = `${req.protocol}://${req.get('host')}/api/v1/auth/verify-email/${verificationToken}`;

        const emailMessage = `
Welcome to TirTir Cosmetics! 🎉

Thank you for registering. Please verify your email address by clicking the link below:

${verificationUrl}

This link will expire in 24 hours.

If you did not create this account, please ignore this email.

Best regards,
TirTir Team
        `.trim();

        const htmlMessage = `
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                <h2 style="color: #FF6B9D;">Welcome to TirTir Cosmetics! 🎉</h2>
                <p>Thank you for registering with us. Please verify your email address to activate your account.</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="${verificationUrl}" 
                       style="display: inline-block; padding: 12px 30px; background-color: #FF6B9D; color: white; text-decoration: none; border-radius: 5px; font-weight: bold;">
                        Verify Email Address
                    </a>
                </div>
                <p>Or copy and paste this link into your browser:</p>
                <p style="color: #666; word-break: break-all; background: #f5f5f5; padding: 10px; border-radius: 5px;">${verificationUrl}</p>
                <hr style="margin: 30px 0; border: none; border-top: 1px solid #eee;">
                <p style="color: #999; font-size: 12px;">This link will expire in 24 hours. If you did not create this account, please ignore this email.</p>
            </div>
        `;

        // ===== TRY TO SEND EMAIL (Graceful Failure) =====
        try {
            await sendEmail({
                email: newUser.email,
                subject: 'Verify Your Email - TirTir Cosmetics',
                message: emailMessage,
                html: htmlMessage
            });

            console.log(`📧 Verification email sent to: ${email}`);

            // Email sent successfully
            return res.status(201).json({
                success: true,
                message: 'Registration successful! Please check your email to verify your account.'
            });

        } catch (emailError) {
            // ===== GRACEFUL EMAIL FAILURE =====
            // Log the error but DON'T crash the server
            console.error('⚠️ Email sending failed:', emailError.message);
            console.error('   User ID:', newUser._id);
            console.error('   Email:', newUser.email);

            // User is created but email failed
            // Return 201 Created with warning message
            return res.status(201).json({
                success: true,
                warning: true,
                message: 'Registration successful, but verification email could not be sent. Please contact support or try to resend verification email.',
                userId: newUser._id
            });
        }

    } catch (error) {
        console.error('❌ Registration Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error during registration. Please try again later.'
        });
    }
};

/**
 * @route   GET /api/v1/auth/verify-email/:token
 * @desc    Verify user email and redirect to frontend
 * @access  Public
 */
exports.verifyEmail = async (req, res) => {
    try {
        const { token } = req.params;

        const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:4200';

        if (!token) {
            return res.status(400).send(`
                <h1>Invalid Request</h1>
                <p>Verification token is missing.</p>
                <a href="${frontendUrl}/login">Go to Login</a>
            `);
        }

        // Hash the token to match database
        const hashedToken = crypto
            .createHash('sha256')
            .update(token)
            .digest('hex');

        // Find user with matching verification token
        const user = await User.findOne({
            emailVerificationToken: hashedToken
        });

        if (!user) {
            return res.status(400).send(`
                <h1>Verification Failed</h1>
                <p>Invalid or expired verification token.</p>
                <a href="${frontendUrl}/login">Go to Login</a>
            `);
        }

        // Check if already verified
        if (user.isEmailVerified) {
            // Redirect anyway
            return res.redirect(`${frontendUrl}/login?verified=true&already=true`);
        }

        // Update user: verify email and clear token
        user.isEmailVerified = true;
        user.emailVerificationToken = undefined;
        await user.save();

        // Redirect to frontend login page with success message
        return res.redirect(`${frontendUrl}/login?verified=true`);

    } catch (error) {
        console.error('❌ Email Verification Error:', error);
        const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:4200';
        return res.status(500).send(`
            <h1>Server Error</h1>
            <p>Something went wrong during email verification.</p>
            <a href="${frontendUrl}/login">Go to Login</a>
        `);
    }
};

/**
 * @route   POST /api/v1/auth/login
 * @desc    Login user and return JWT token
 * @access  Public
 */
exports.login = async (req, res) => {
    try {
        const { email, password } = req.body;

        // ===== VALIDATION =====
        if (!email || !password) {
            return res.status(400).json({
                success: false,
                message: 'Please provide email and password'
            });
        }

        // ===== CHECK IF USER EXISTS =====
        // Explicitly select password because it's set to select: false in schema
        const user = await User.findOne({ email }).select('+password');

        if (!user) {
            return res.status(401).json({
                success: false,
                message: 'Invalid email or password'
            });
        }

        // ===== CHECK EMAIL VERIFICATION =====
        if (!user.isEmailVerified) {
            return res.status(401).json({
                success: false,
                message: 'Please verify your email before logging in. Check your inbox for the verification link.',
                requiresVerification: true
            });
        }

        // ===== VERIFY PASSWORD =====
        const isPasswordMatch = await user.matchPassword(password);

        if (!isPasswordMatch) {
            return res.status(401).json({
                success: false,
                message: 'Invalid email or password'
            });
        }

        // Check if user is blocked
        if (user.isBlocked) {
            return res.status(403).json({
                success: false,
                message: 'Your account has been blocked. Please contact support.'
            });
        }

        // ===== GENERATE TOKENS =====
        const accessToken = generateAccessToken(user._id, user.role);
        const refreshToken = await generateAndSaveRefreshToken(user);

        // Log Activity
        logActivity(req, 'AUTH', 'LOGIN', `User logged in: ${email}`, { userId: user._id });

        // ===== SET REFRESH TOKEN AS HTTP-ONLY COOKIE (security hardening) =====
        const isProduction = process.env.NODE_ENV === 'production';
        const expireDays = parseInt(process.env.JWT_REFRESH_EXPIRE_DAYS || '30', 10);
        res.cookie('refreshToken', refreshToken, {
            httpOnly: true,
            secure: isProduction,      // HTTPS only in production
            sameSite: isProduction ? 'strict' : 'lax',
            maxAge: expireDays * 24 * 60 * 60 * 1000
        });

        // ===== SUCCESSFUL LOGIN =====
        return res.status(200).json({
            success: true,
            token: accessToken,
            refreshToken, // Retained for Angular SPA compatibility
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                role: user.role,
                isEmailVerified: user.isEmailVerified
            }
        });

    } catch (error) {
        console.error('❌ Login Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error during login. Please try again later.'
        });
    }
};

/**
 * @route   GET /api/v1/auth/me
 * @desc    Get current logged-in user
 * @access  Private (requires protect middleware)
 */
exports.getMe = async (req, res) => {
    try {
        // req.user is set by protect middleware
        const user = await User.findById(req.user.id).select('-password');

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        return res.status(200).json({
            success: true,
            data: user
        });

    } catch (error) {
        console.error('❌ Get Me Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error'
        });
    }
};

/**
 * @route   POST /api/v1/auth/forgot-password
 * @desc    Send password reset email
 * @access  Public
 */
exports.forgotPassword = async (req, res) => {
    try {
        const { email } = req.body;

        if (!email) {
            return res.status(400).json({
                success: false,
                message: 'Please provide an email address'
            });
        }

        const user = await User.findOne({ email });

        // ===== ANTI-ENUMERATION: Always return 200 regardless of whether email exists =====
        // Never reveal whether an email is registered in the system
        if (!user) {
            return res.status(200).json({
                success: true,
                message: 'If an account exists with this email, a recovery link has been sent'
            });
        }

        // Generate reset token
        const resetToken = user.getResetPasswordToken();
        await user.save({ validateBeforeSave: false });

        // Create reset URL
        const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:4200';
        const resetUrl = `${frontendUrl}/reset-password/${resetToken}`;

        const message = `You are receiving this email because you (or someone else) requested to reset your password.\n\nPlease click the link below to reset your password:\n\n${resetUrl}\n\nThis link will expire in 10 minutes.\n\nIf you did not request this, please ignore this email.`;

        const htmlMessage = `
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2 style="color: #FF6B9D;">Password Reset Request</h2>
                <p>You are receiving this email because you (or someone else) requested to reset your password.</p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="${resetUrl}" 
                       style="display: inline-block; padding: 12px 30px; background-color: #FF6B9D; color: white; text-decoration: none; border-radius: 5px; font-weight: bold;">
                        Reset Password
                    </a>
                </div>
                <p>Or copy and paste this link into your browser:</p>
                <p style="color: #666; word-break: break-all;">${resetUrl}</p>
                <p><strong>This link will expire in 10 minutes.</strong></p>
                <p>If you did not request this, please ignore this email.</p>
            </div>
        `;

        try {
            await sendEmail({
                email: user.email,
                subject: 'Password Reset Request - TirTir Cosmetics',
                message,
                html: htmlMessage
            });

            return res.status(200).json({
                success: true,
                message: 'If an account exists with this email, a recovery link has been sent'
            });

        } catch (emailError) {
            console.error('⚠️ Password reset email failed:', emailError.message);

            // Clear reset token if email fails
            user.resetPasswordToken = undefined;
            user.resetPasswordExpire = undefined;
            await user.save({ validateBeforeSave: false });

            return res.status(500).json({
                success: false,
                message: 'Email could not be sent. Please try again later.'
            });
        }

    } catch (error) {
        console.error('❌ Forgot Password Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error'
        });
    }
};

/**
 * @route   PUT /api/v1/auth/reset-password/:resettoken
 * @desc    Reset user password
 * @access  Public
 */
exports.resetPassword = async (req, res) => {
    try {
        const { password } = req.body;

        if (!password) {
            return res.status(400).json({
                success: false,
                message: 'Please provide a new password'
            });
        }

        // Password strength validation (NIST SP 800-63B: minimum 8 characters)
        if (password.length < 8) {
            return res.status(400).json({
                success: false,
                message: 'Password must be at least 8 characters long'
            });
        }

        // Hash the token from URL
        const hashedToken = crypto
            .createHash('sha256')
            .update(req.params.resettoken)
            .digest('hex');

        // Find user with valid reset token
        const user = await User.findOne({
            resetPasswordToken: hashedToken,
            resetPasswordExpire: { $gt: Date.now() }
        });

        if (!user) {
            return res.status(400).json({
                success: false,
                message: 'Invalid or expired reset token'
            });
        }

        // Set new password (will be hashed by pre-save hook)
        user.password = password;
        user.resetPasswordToken = undefined;
        user.resetPasswordExpire = undefined;
        await user.save();

        // Generate tokens for immediate login
        const accessToken = generateAccessToken(user._id, user.role);
        const refreshToken = await generateAndSaveRefreshToken(user);

        // ===== SET REFRESH TOKEN AS HTTP-ONLY COOKIE (security hardening) =====
        const isProduction = process.env.NODE_ENV === 'production';
        const expireDays = parseInt(process.env.JWT_REFRESH_EXPIRE_DAYS || '30', 10);
        res.cookie('refreshToken', refreshToken, {
            httpOnly: true,
            secure: isProduction,
            sameSite: isProduction ? 'strict' : 'lax',
            maxAge: expireDays * 24 * 60 * 60 * 1000
        });

        return res.status(200).json({
            success: true,
            message: 'Password reset successful',
            token: accessToken,
            refreshToken, // Retained for Angular SPA compatibility
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                role: user.role
            }
        });

    } catch (error) {
        console.error('❌ Reset Password Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error'
        });
    }
};

/**
 * @route   POST /api/v1/auth/logout
 * @desc    Logout user — invalidates refresh token in DB and clears the cookie
 * @access  Private (requires protect middleware)
 */
exports.logout = async (req, res) => {
    try {
        // ── 1. Invalidate refresh token in the database ──────────────────────
        // req.user is guaranteed by the protect middleware; we null out both
        // fields so the token cannot be reused even if intercepted later.
        if (req.user?.id) {
            await User.findByIdAndUpdate(req.user.id, {
                $unset: { refreshToken: '', refreshTokenExpire: '' }
            });
            console.log(`🔒 Logout: refresh token invalidated for user ${req.user.id}`);
        }

        // ── 2. Clear the HTTP-only refresh token cookie on the client ────────
        // Setting maxAge to 0 (or expires to the past) instructs the browser
        // to delete the cookie immediately.
        const isProduction = process.env.NODE_ENV === 'production';
        res.cookie('refreshToken', '', {
            httpOnly: true,
            secure: isProduction,
            sameSite: isProduction ? 'strict' : 'lax',
            expires: new Date(0)   // epoch — forces immediate expiry
        });

        return res.status(200).json({
            success: true,
            message: 'Logged out successfully.'
        });

    } catch (error) {
        console.error('❌ Logout Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error during logout.'
        });
    }
};

/**
 * @route   POST /api/v1/auth/refresh-token
 * @desc    Refresh JWT token (placeholder for future implementation)
 * @access  Public
 */
/**
 * @route   POST /api/v1/auth/refresh-token
 * @desc    Issue a new access token + rotate refresh token
 * @access  Public (requires valid refreshToken in body)
 */
exports.refreshToken = async (req, res) => {
    try {
        const { refreshToken } = req.body;

        if (!refreshToken) {
            return res.status(400).json({ success: false, message: 'Refresh token is required' });
        }

        // Hash the incoming token to compare with DB
        const hashed = crypto.createHash('sha256').update(refreshToken).digest('hex');

        // Find user with matching, non-expired refresh token
        const user = await User.findOne({
            refreshToken: hashed,
            refreshTokenExpire: { $gt: Date.now() }
        }).select('+refreshToken +refreshTokenExpire');

        if (!user) {
            return res.status(401).json({
                success: false,
                message: 'Invalid or expired refresh token. Please login again.'
            });
        }

        if (user.isBlocked) {
            return res.status(403).json({ success: false, message: 'Your account has been blocked.' });
        }

        // Issue new access token
        const newAccessToken = generateAccessToken(user._id, user.role);

        // Rotate refresh token (invalidate old one, issue new one)
        const newRefreshToken = await generateAndSaveRefreshToken(user);

        console.log(`🔄 Token refreshed for user: ${user.email}`);

        return res.status(200).json({
            success: true,
            token: newAccessToken,
            refreshToken: newRefreshToken
        });

    } catch (error) {
        console.error('❌ Refresh Token Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error during token refresh'
        });
    }
};
