const mongoose = require('mongoose');

const CartSchema = new mongoose.Schema({
    user: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        // Enable guests by removing strict constraint, use sparse unique index
        index: { unique: true, sparse: true }
    },
    session_id: {
        type: String,
        index: { unique: true, sparse: true }
    },
    status: {
        type: String,
        enum: ['active', 'purchased', 'abandoned', 'merged'],
        default: 'active'
    },
    recovery_token: {
        type: String,
        sparse: true
    },
    token_expires_at: {
        type: Date
    },
    merged_into_cart_id: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Cart'
    },
    items: [
        {
            product: {
                type: mongoose.Schema.Types.ObjectId,
                ref: 'Product',
                required: true
            },
            quantity: {
                type: Number,
                required: true,
                min: 1
            },
            shade: {
                type: String,
                default: '' // Default to empty string if not provided
            },
            price: {
                type: Number,
                required: true,
                default: 0
            }
        }
    ],
    totalPrice: {
        type: Number,
        required: true,
        default: 0
    },
    recoveryStatus: {
        type: String,
        enum: ['pending', 'email_1_sent', 'email_2_sent', 'email_3_sent', 'recovered', 'abandoned_final', 'unsubscribed'],
        default: 'pending'
    },
    unsubscribedAt: {
        type: Date,
        default: null
    },
    isProcessing: {
        type: Boolean,
        default: false
    },
    lastAbandonedAt: {
        type: Date
    },
    recoveryMetrics: {
        email1SentAt: Date,
        email2SentAt: Date,
        email3SentAt: Date
    }
}, { timestamps: true });

// Optimize query for abandoned carts cron
CartSchema.index({ recoveryStatus: 1, lastAbandonedAt: -1 });

module.exports = mongoose.model('Cart', CartSchema);
