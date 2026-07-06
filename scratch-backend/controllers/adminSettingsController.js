const User = require('../backend/models/user.model');

// Update Preferences (Theme, Language, Critical Alerts, 2FA)
exports.updatePreferences = async (req, res) => {
    try {
        const { theme, language, criticalAlerts, twoFactorEnabled } = req.body;
        const user = await User.findById(req.user.id);

        if (!user) {
            return res.status(404).json({ success: false, message: 'Admin not found' });
        }

        if (theme) user.preferences.theme = theme;
        if (language) user.preferences.language = language;
        if (criticalAlerts !== undefined) user.preferences.criticalAlerts = criticalAlerts;
        if (twoFactorEnabled !== undefined) user.twoFactorEnabled = twoFactorEnabled;

        await user.save();

        res.status(200).json({
            success: true,
            data: {
                preferences: user.preferences,
                twoFactorEnabled: user.twoFactorEnabled
            },
            message: 'Preferences updated successfully'
        });
    } catch (error) {
        res.status(500).json({ success: false, message: error.message });
    }
};

// Change Password for authenticated user
exports.changePassword = async (req, res) => {
    try {
        const { currentPassword, newPassword } = req.body;
        
        // Ensure user password is included
        const user = await User.findById(req.user.id).select('+password');
        if (!user) {
            return res.status(404).json({ success: false, message: 'Admin not found' });
        }

        // Check if current password matches
        const isMatch = await user.matchPassword(currentPassword);
        if (!isMatch) {
            return res.status(401).json({ success: false, message: 'Invalid current password' });
        }

        user.password = newPassword;
        await user.save();

        res.status(200).json({ success: true, message: 'Password updated successfully' });
    } catch (error) {
        res.status(500).json({ success: false, message: error.message });
    }
};

// Get Mock Login History
exports.getLoginHistory = async (req, res) => {
    res.status(200).json({
        success: true,
        data: [
            { ip: '192.168.1.1', location: 'London, UK', date: new Date().toISOString(), status: 'Success' },
            { ip: '192.168.1.5', location: 'London, UK', date: new Date(Date.now() - 86400000).toISOString(), status: 'Success' },
            { ip: '24.12.34.11', location: 'Paris, FR', date: new Date(Date.now() - 172800000).toISOString(), status: 'Failed' }
        ]
    });
};

// Get Mock API Logs
exports.getApiLogs = async (req, res) => {
    res.status(200).json({
        success: true,
        data: [
            { method: 'POST', endpoint: '/api/v1/products', statusCode: 201, time: new Date().toISOString() },
            { method: 'GET', endpoint: '/api/v1/orders', statusCode: 200, time: new Date(Date.now() - 3600000).toISOString() }
        ]
    });
};

// Get Mock Audit Trails
exports.getAuditTrails = async (req, res) => {
    res.status(200).json({
        success: true,
        data: [
            { action: 'Updated Settings', user: 'Elena Rossi', time: new Date().toISOString() },
            { action: 'Deleted Product', user: 'Elena Rossi', time: new Date(Date.now() - 7200000).toISOString() },
            { action: 'Invited Member', user: 'Elena Rossi', time: new Date(Date.now() - 86400000).toISOString() }
        ]
    });
};
