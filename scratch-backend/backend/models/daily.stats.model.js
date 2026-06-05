const mongoose = require('mongoose');

const DailyStatsSchema = new mongoose.Schema({
    date: { type: String, required: true, unique: true }, // Format: YYYY-MM-DD
    views: { type: Number, default: 0 },
    addToCart: { type: Number, default: 0 },
    orders: { type: Number, default: 0 }, // Synced from Order creation
    revenue: { type: Number, default: 0 }
}, { timestamps: true });

module.exports = mongoose.model('DailyStats', DailyStatsSchema);
