const mongoose = require('mongoose');

const scanHistorySchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  barcodeValue: { type: String, required: true, unique: true, index: true },
  pointsEarned: { type: Number, default: 50 }
}, { timestamps: true });

module.exports = mongoose.model('ScanHistory', scanHistorySchema);
