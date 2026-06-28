const mongoose = require('mongoose');

const routineStepSchema = new mongoose.Schema({
  stepType: { type: String, required: true },
  productId: { type: String },
  order: { type: Number, default: 1 },
  stepName: { type: String },
  productName: { type: String },
  notes: { type: String, default: '' }
}, { _id: false });

const routineSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.Mixed, required: true, index: true },
  ownerId: { type: String }, // String representation of userId
  userName: { type: String, default: 'Anonymous' },
  name: { type: String, required: true },
  description: { type: String, default: '' },
  steps: [routineStepSchema],
  isPublic: { type: Boolean, default: false, index: true },
  likeCount: { type: Number, default: 0 },
  likes: { type: Number, default: 0 }, // backward compatibility
  likedBy: [{ type: String }], // userIds who liked this routine
  applyCount: { type: Number, default: 0 },
  isMorning: { type: Boolean, default: true }
}, { timestamps: true });


routineSchema.pre('save', function(next) {
  if (this.userId && !this.ownerId) {
    this.ownerId = String(this.userId);
  }
  if (this.likeCount === 0 && this.likes > 0) {
    this.likeCount = this.likes;
  }
  next();
});

module.exports = mongoose.model('Routine', routineSchema);
