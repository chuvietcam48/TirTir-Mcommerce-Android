const ActivityLog = require('../models/activity.log.model');
const logger = require('./logger');

/**
 * Log a user activity to database and winston
 * @param {Object} req - Express request object (optional, for IP/User context)
 * @param {String} module - Module name (e.g., 'AUTH', 'PRODUCT')
 * @param {String} action - Action name (e.g., 'LOGIN', 'UPDATE')
 * @param {String} description - Human readable description
 * @param {Object} details - Additional metadata
 * @param {String} status - 'SUCCESS', 'FAILURE', 'WARNING'
 */
const logActivity = async (req, module, action, description, details = {}, status = 'SUCCESS') => {
    try {
        const user = req?.user?._id || req?.user?.id || null;
        const ip = req?.headers?.['x-forwarded-for'] || req?.socket?.remoteAddress || '';
        const userAgent = req?.headers?.['user-agent'] || '';

        // 1. Write to Database for Analytics
        await ActivityLog.create({
            user,
            module,
            action,
            description,
            details,
            ip,
            userAgent,
            status
        });

        // 2. Write to File Logs for Auditing
        logger.info(`[ACTIVITY] [${module}] ${action} - ${description} - User: ${user || 'Guest'} - Status: ${status}`);

    } catch (error) {
        logger.error(`Failed to log activity: ${error.message}`);
    }
};

module.exports = { logActivity };
