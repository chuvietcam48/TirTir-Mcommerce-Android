const DailyStats = require('../backend/models/daily.stats.model');

exports.trackEvent = async (req, res) => {
    try {
        const { eventType, campaignId, metadata } = req.body;
        
        const date = new Date().toISOString().split('T')[0];
        
        const updateObj = {};
        if (eventType === 'view' || eventType === 'page_view') {
            updateObj.$inc = { views: 1 };
        } else if (eventType === 'click') {
            updateObj.$inc = { clicks: 1 };
        } else if (eventType === 'add_to_cart') {
            updateObj.$inc = { addToCart: 1 };
        } else {
            return res.status(400).json({ success: false, message: 'Invalid event type' });
        }

        await DailyStats.findOneAndUpdate(
            { date },
            updateObj,
            { upsert: true, new: true }
        );

        res.status(200).json({ success: true, message: 'Event tracked successfully' });
    } catch (error) {
        console.error('Tracking error:', error);
        res.status(500).json({ success: false, message: 'Server error during tracking' });
    }
};
