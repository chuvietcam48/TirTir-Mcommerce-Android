const mongoose = require('mongoose');

const EVENT_TYPES = [
  'cart_abandoned', 'email_queued', 'email_sent', 'email_opened', 
  'email_clicked', 'email_bounced', 'email_spam_complaint', 
  'cart_recovered', 'job_failed', 'token_used', 'token_expired', 'unsubscribed',
  'cart_merged'
];

const CartRecoveryEventSchema = new mongoose.Schema({
  cart_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Cart', required: true },
  user_id: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
  session_id: { type: String, index: true },
  event_type: { type: String, enum: EVENT_TYPES, required: true },
  email_sequence: { type: Number, enum: [1, 2, 3] },
  metadata: { type: mongoose.Schema.Types.Mixed, default: {} },
  created_at: { type: Date, default: Date.now }
});

// INDEXES (Block 1 Refining)
CartRecoveryEventSchema.index({ cart_id: 1, created_at: -1 });
CartRecoveryEventSchema.index({ event_type: 1, created_at: -1 });
CartRecoveryEventSchema.index({ 'metadata.message_id': 1 }, { sparse: true });

module.exports = mongoose.model('CartRecoveryEvent', CartRecoveryEventSchema);
