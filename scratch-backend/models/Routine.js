const mongoose = require('mongoose');

const routineStepSchema = new mongoose.Schema({
  stepNumber: { type: Number },
  stepName: { type: String, required: true },
  productId: { type: String },
  productName: { type: String },
  notes: { type: String, default: '' }
}, { _id: false });

const routineSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  userName: { type: String, default: 'Anonymous' },
  name: { type: String, default: 'My Custom Routine' },
  description: { type: String, default: '' },
  steps: [routineStepSchema],
  items: [{ type: Object }], // backward compatibility
  isPublic: { type: Boolean, default: false },
  likes: { type: Number, default: 0 },
  isMorning: { type: Boolean, default: true }
}, { timestamps: true });

module.exports = mongoose.model('Routine', routineSchema);
