const express = require('express');
const router = express.Router();
const User = require('../models/user.model');

/**
 * DEBUG ROUTE - GET user details
 * @route   GET /api/v1/debug/user/:email
 * @desc    Get full user details for debugging
 * @access  Public (for debugging only - remove in production)
 */
router.get('/user/:email', async (req, res) => {
    try {
        const user = await User.findOne({ email: req.params.email });

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        return res.status(200).json({
            success: true,
            user: {
                email: user.email,
                name: user.name,
                role: user.role,
                isEmailVerified: user.isEmailVerified,
                emailVerificationToken: user.emailVerificationToken,
                isBlocked: user.isBlocked,
                createdAt: user.createdAt,
                updatedAt: user.updatedAt
            }
        });
    } catch (error) {
        console.error('Debug route error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error',
            error: error.message
        });
    }
});

module.exports = router;
