const Setting = require('../models/setting.model');

// GET /api/v1/settings
// Get system settings (public or admin? Maybe public for some parts, but admin for all)
// Usually frontend needs some settings (banner, contact) publicly.
// But this is admin panel.
exports.getSettings = async (req, res) => {
    try {
        let settings = await Setting.findOne();
        if (!settings) {
            // Create default if not exists
            settings = await Setting.create({});
        }
        res.json(settings);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// PUT /api/v1/settings
// Update system settings (Admin only)
exports.updateSettings = async (req, res) => {
    try {
        const updates = req.body;
        let settings = await Setting.findOne();
        
        if (!settings) {
            settings = new Setting(updates);
        } else {
            // Update fields
            Object.keys(updates).forEach(key => {
                settings[key] = updates[key];
            });
        }
        
        await settings.save();
        res.json({ success: true, settings });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// Public endpoint for storefront (if needed later)
exports.getPublicSettings = async (req, res) => {
    try {
        const settings = await Setting.findOne().select('bannerUrl shippingFee freeShippingThreshold contactPhone contactEmail socialLinks -_id');
        res.json(settings || {});
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};
