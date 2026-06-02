const rateLimit = require('express-rate-limit');
const { ipKeyGenerator } = require('express-rate-limit');

// General API rate limit (1000 requests per 15 minutes in dev)
exports.apiLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: process.env.NODE_ENV === 'development' ? 1000 : 100, // Limit each IP
    standardHeaders: true,
    legacyHeaders: false,
    skip: (req, res) => process.env.NODE_ENV === 'development', // Skip in dev if needed, or just high max
    message: {
        success: false,
        message: 'Too many requests from this IP, please try again after 15 minutes'
    }
});

// Stricter limit for Auth routes (Login/Register) - 5 attempts per 15 minutes
exports.authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 5, // Limit each IP to 5 requests per windowMs
    standardHeaders: true,
    legacyHeaders: false,
    // Skip rate limiting in development so testing doesn't get blocked
    skip: () => process.env.NODE_ENV === 'development',
    message: {
        success: false,
        message: 'Too many login attempts, please try again after 15 minutes'
    }
});

// ─── Per-User Rate Limiter ────────────────────────────────────────────────────
// Uses authenticated userId as the key — prevents users from hammering
// sensitive endpoints even if they share an IP (e.g. behind a corporate proxy).
// Falls back to IP if user is not authenticated.

const createUserLimiter = (max, windowMs, message) => rateLimit({
    windowMs,
    max,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => {
        // Use userId if authenticated, else fall back to IP
        return req.user?.id?.toString() || ipKeyGenerator(req);
    },
    message: {
        success: false,
        message: message || `Too many requests, please try again later`
    }
});

// 10 checkout attempts per hour per user
exports.checkoutLimiter = createUserLimiter(
    10,
    60 * 60 * 1000,
    'Too many orders placed. Please wait before placing another order.'
);

// 5 review submissions per hour per user
exports.reviewLimiter = createUserLimiter(
    5,
    60 * 60 * 1000,
    'Too many reviews submitted. You can submit up to 5 reviews per hour.'
);

// 20 AI skin scans per hour per user
exports.aiScanLimiter = createUserLimiter(
    20,
    60 * 60 * 1000,
    'Too many AI scan requests. You can perform up to 20 scans per hour.'
);

