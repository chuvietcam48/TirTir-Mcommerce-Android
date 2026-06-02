const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');

/**
 * User Schema for TirTir Cosmetics Platform
 * Handles authentication, email verification, and password management
 */
const UserSchema = new mongoose.Schema({
    name: {
        type: String,
        required: [true, 'Please provide a name'],
        trim: true
    },
    email: {
        type: String,
        required: [true, 'Please provide an email'],
        unique: true,
        trim: true,
        lowercase: true,
        match: [
            /^\w+([\.-]?\w+)*@\w+([\.-]?\w+)*(\.\w{2,3})+$/,
            'Please provide a valid email'
        ]
    },
    password: {
        type: String,
        required: [true, 'Please provide a password'],
        minlength: [6, 'Password must be at least 6 characters'],
        select: false // Security: Do not return password by default
    },
    role: {
        type: String,
        enum: ['user', 'admin', 'inventory_staff', 'customer_service'],
        default: 'user'
    },
    isBlocked: { // New field for banning users
        type: Boolean,
        default: false
    },
    isEmailVerified: {
        type: Boolean,
        default: false
    },
    resetPasswordToken: String,
    emailVerificationToken: String,
    resetPasswordExpire: Date,
    refreshToken: { type: String, select: false },
    refreshTokenExpire: { type: Date },
    // ===== NEW PROFILE FIELDS =====
    avatar: {
        type: String,
        default: null
    },
    phone: {
        type: String,
        trim: true,
        default: null
    },
    gender: {
        type: String,
        enum: ['Male', 'Female', 'Other'],
        default: null
    },
    birthDate: {
        type: Date,
        default: null
    },
    // ===== ADDRESS BOOK (Sub-document Array) =====
    addresses: [{
        fullName: {
            type: String,
            required: [true, 'Full name is required for address'],
            trim: true
        },
        phone: {
            type: String,
            required: [true, 'Phone number is required for address'],
            trim: true
        },
        street: {
            type: String,
            required: [true, 'Street address is required'],
            trim: true
        },
        city: {
            type: String,
            required: [true, 'City is required'],
            trim: true
        },
        district: {
            type: String,
            required: [true, 'District is required'],
            trim: true
        },
        ward: {
            type: String,
            required: [true, 'Ward is required'],
            trim: true
        },
        isDefault: {
            type: Boolean,
            default: false
        }
    }],
    // ===== SKIN PROFILE (Latest AI Analysis Snapshot) =====
    skinProfile: {
        skinTone:       { type: String, default: null },
        undertone:      { type: String, default: null },
        skinType:       { type: String, default: null },
        concerns:       { type: [String], default: [] },
        confidence:     { type: Number, default: null },
        lastAnalyzedAt: { type: Date, default: null }
    },
    // ===== WISHLIST (Product References) =====
    wishlist: [{
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Product'
    }]
}, {
    timestamps: true
});

// ===== MIDDLEWARE: Pre-Save Hook for Password Hashing =====
UserSchema.pre('save', async function () {
    // Only hash password if it has been modified (or is new)
    if (!this.isModified('password')) {
        return;
    }

    try {
        console.log('HOOK: Hashing password...');
        const salt = await bcrypt.genSalt(10);
        this.password = await bcrypt.hash(this.password, salt);
        console.log('HOOK: Password hashed successfully.');
    } catch (error) {
        console.error('HOOK: Error hashing password:', error);
        throw error; // Mongoose will catch this rejected promise
    }
});

// ===== INSTANCE METHOD: Match Password =====
/**
 * Compare entered password with hashed password in database
 * @param {String} enteredPassword - Plain text password from login attempt
 * @returns {Promise<Boolean>} - True if passwords match, false otherwise
 */
UserSchema.methods.matchPassword = async function (enteredPassword) {
    try {
        return await bcrypt.compare(enteredPassword, this.password);
    } catch (error) {
        throw new Error(`Password comparison failed: ${error.message}`);
    }
};

// ===== INSTANCE METHOD: Generate Email Verification Token =====
/**
 * Generate and hash email verification token
 * Returns unhashed token for sending in email
 * Stores hashed version in database for security
 */
UserSchema.methods.getEmailVerificationToken = function () {
    // Generate random token (20 bytes = 40 hex characters)
    const verificationToken = crypto.randomBytes(20).toString('hex');

    // Hash token and store in database
    this.emailVerificationToken = crypto
        .createHash('sha256')
        .update(verificationToken)
        .digest('hex');

    // Return unhashed token (to send in email)
    return verificationToken;
};

// ===== INSTANCE METHOD: Generate Password Reset Token =====
/**
 * Generate and hash password reset token with expiration
 * Returns unhashed token for sending in email
 */
UserSchema.methods.getResetPasswordToken = function () {
    // Generate random token
    const resetToken = crypto.randomBytes(20).toString('hex');

    // Hash token and store in database
    this.resetPasswordToken = crypto
        .createHash('sha256')
        .update(resetToken)
        .digest('hex');

    // Set expiration time (10 minutes from now)
    this.resetPasswordExpire = Date.now() + 10 * 60 * 1000;

    // Return unhashed token
    return resetToken;
};

module.exports = mongoose.model('User', UserSchema);
