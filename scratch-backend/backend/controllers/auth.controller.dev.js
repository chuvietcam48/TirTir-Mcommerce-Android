/**
 * 🧪 DEVELOPMENT ONLY: TEMPORARY EMAIL BYPASS
 * 
 * This script modifies the register controller to SKIP email verification
 * in development mode. Use this ONLY for testing when email is not configured.
 * 
 * WARNING: DO NOT USE IN PRODUCTION!
 * 
 * Usage: 
 * 1. Set NODE_ENV=development in your .env
 * 2. Users will be auto-verified on registration
 * 3. No email will be sent
 */

const User = require('../models/user.model');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const sendEmail = require('../utils/sendEmail');

// Helper function to get JWT_SECRET
const getJwtSecret = () => {
    const secret = process.env.JWT_SECRET;
    if (!secret) {
        throw new Error('JWT_SECRET is not defined in environment variables');
    }
    return secret;
};

// Register Controller - WITH EMAIL BYPASS FOR DEVELOPMENT
exports.registerWithBypass = async (req, res) => {
    try {
        if (!req.body) {
            return res.status(400).json({ message: "Request body is empty" });
        }
        const { name, email, password } = req.body;

        // Validation
        if (!name || !email || !password) {
            return res.status(400).json({ message: "All fields are required" });
        }

        // Email format validation
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(email)) {
            return res.status(400).json({ message: "Invalid email format" });
        }

        // Password strength validation
        if (password.length < 6) {
            return res.status(400).json({ message: "Password must be at least 6 characters" });
        }

        // Check if user exists
        const existingUser = await User.findOne({ email });
        if (existingUser) {
            return res.status(400).json({ message: "User already exists" });
        }

        // Hash password
        const salt = await bcrypt.genSalt(10);
        const hashedPassword = await bcrypt.hash(password, salt);

        // 🔥 DEVELOPMENT MODE: Skip email verification
        const isDevelopment = process.env.NODE_ENV === 'development';

        const newUser = new User({
            name,
            email,
            password: hashedPassword,
            isEmailVerified: isDevelopment // Auto-verify in dev mode
        });

        // Save user
        await newUser.save({ validateBeforeSave: false });

        if (isDevelopment) {
            // Skip email sending, auto-login the user
            console.log('🔥 DEV MODE: Email verification skipped for', email);

            const token = jwt.sign(
                { id: newUser._id, role: newUser.role },
                getJwtSecret(),
                { expiresIn: '7d' }
            );

            return res.status(200).json({
                success: true,
                message: 'Registration successful! (DEV MODE: Email verification skipped)',
                token,
                user: {
                    id: newUser._id,
                    name: newUser.name,
                    email: newUser.email,
                    role: newUser.role
                }
            });
        } else {
            // PRODUCTION MODE: Send email as normal
            const verificationToken = newUser.getEmailVerificationToken();
            await newUser.save({ validateBeforeSave: false });

            const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:4200';
            const verificationUrl = `${frontendUrl}/verify-email/${verificationToken}`;

            const message = `Welcome to TirTir Shop!\n\nPlease verify your email address by clicking the link below:\n\n${verificationUrl}\n\nIf you did not create this account, please ignore this email.`;

            const htmlMessage = `
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #FF6B9D;">Welcome to TirTir Shop! 🎉</h2>
                    <p>Thank you for registering with us. Please verify your email address to activate your account.</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="${verificationUrl}" 
                           style="display: inline-block; padding: 12px 30px; background-color: #FF6B9D; color: white; text-decoration: none; border-radius: 5px; font-weight: bold;">
                            Verify Email Address
                        </a>
                    </div>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="color: #666; word-break: break-all;">${verificationUrl}</p>
                    <hr style="margin: 30px 0; border: none; border-top: 1px solid #eee;">
                    <p style="color: #999; font-size: 12px;">If you did not create this account, please ignore this email.</p>
                </div>
            `;

            try {
                await sendEmail({
                    email: newUser.email,
                    subject: 'Verify Your Email - TirTir Shop',
                    message,
                    html: htmlMessage
                });

                res.status(200).json({
                    success: true,
                    message: 'Registration successful! Please check your email to verify your account.'
                });
            } catch (err) {
                console.error('Email send error:', err);
                await User.findByIdAndDelete(newUser._id);

                return res.status(500).json({
                    success: false,
                    message: 'Email could not be sent. Please try registering again.'
                });
            }
        }

    } catch (error) {
        console.error("Register Error:", error);
        res.status(500).json({ message: "Server error" });
    }
};
