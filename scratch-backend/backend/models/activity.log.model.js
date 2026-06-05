const mongoose = require('mongoose');

const ActivityLogSchema = new mongoose.Schema({
    user: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: false // Might be system action or unauthenticated
    },
    action: {
        type: String,
        required: true,
        uppercase: true,
        index: true
    },
    module: {
        type: String,
        required: true,
        index: true
    },
    description: {
        type: String,
        required: true
    },
    details: {
        type: Object,
        default: {}
    },
    ip: {
        type: String
    },
    userAgent: {
        type: String
    },
    status: {
        type: String,
        enum: ['SUCCESS', 'FAILURE', 'WARNING'],
        default: 'SUCCESS'
    }
}, { 
    timestamps: true,
    expireAfterSeconds: 60 * 60 * 24 * 90 // Auto-delete logs after 90 days
});

// Index for faster queries
ActivityLogSchema.index({ createdAt: -1 });
ActivityLogSchema.index({ user: 1 });

module.exports = mongoose.model('ActivityLog', ActivityLogSchema);
