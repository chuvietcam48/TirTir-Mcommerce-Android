const jwt = require('jsonwebtoken');
const User = require('../models/user.model');

// Protect routes - Verify JWT token
exports.protect = async (req, res, next) => {
    try {
        let token;

        // Check if token exists in Authorization header
        if (
            req.headers.authorization &&
            req.headers.authorization.startsWith('Bearer')
        ) {
            // Extract token from "Bearer <token>"
            token = req.headers.authorization.split(' ')[1];
        }
        // Alternative: Check if token is in cookies (if you implement cookie-based auth)
        // else if (req.cookies.token) {
        //     token = req.cookies.token;
        // }

        // Make sure token exists
        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'Not authorized to access this route. Please login.'
            });
        }

        try {
            // Verify token
            const JWT_SECRET = process.env.JWT_SECRET;

            if (!JWT_SECRET) {
                console.error('CRITICAL: JWT_SECRET is not defined in environment variables!');
                return res.status(500).json({
                    success: false,
                    message: 'Server configuration error'
                });
            }

            const decoded = jwt.verify(token, JWT_SECRET);

            // Attach user to request object (exclude password)
            req.user = await User.findById(decoded.id).select('-password');

            if (!req.user) {
                return res.status(401).json({
                    success: false,
                    message: 'User not found. Token may be invalid.'
                });
            }

            next();

        } catch (err) {
            // Handle specific JWT errors
            if (err.name === 'TokenExpiredError') {
                return res.status(401).json({
                    success: false,
                    message: 'Token expired. Please login again.'
                });
            }

            if (err.name === 'JsonWebTokenError') {
                return res.status(401).json({
                    success: false,
                    message: 'Invalid token. Please login again.'
                });
            }

            return res.status(401).json({
                success: false,
                message: 'Not authorized to access this route'
            });
        }

    } catch (error) {
        console.error('Auth Middleware Error:', error);
        res.status(500).json({
            success: false,
            message: 'Server error in authentication'
        });
    }
};

// Grant access to specific roles
exports.authorize = (...roles) => {
    return (req, res, next) => {
        // Check if user exists (should be attached by protect middleware)
        if (!req.user) {
            return res.status(401).json({
                success: false,
                message: 'User not authenticated'
            });
        }

        // Check if user's role is in allowed roles
        if (!roles.includes(req.user.role)) {
            return res.status(403).json({
                success: false,
                message: `User role ${req.user.role} is not authorized to access this route`
            });
        }

        next();
    };
};

// Optional Auth - Try to verify token but don't block if missing/invalid
exports.optionalProtect = async (req, res, next) => {
    try {
        let token;
        if (
            req.headers.authorization &&
            req.headers.authorization.startsWith('Bearer')
        ) {
            token = req.headers.authorization.split(' ')[1];
        }

        if (!token) {
            return next();
        }

        try {
            const JWT_SECRET = process.env.JWT_SECRET;
            if (JWT_SECRET) {
                const decoded = jwt.verify(token, JWT_SECRET);
                req.user = await User.findById(decoded.id).select('-password');
            }
        } catch (err) {
            // Token invalid or expired - just proceed as guest
            // console.log("Optional auth failed:", err.message);
        }
        next();
    } catch (error) {
        next();
    }
};
