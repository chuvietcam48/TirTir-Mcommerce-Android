const mongoose = require('mongoose');

const EmailSendLogSchema = new mongoose.Schema({
  idempotency_key: { type: String, required: true, unique: true }, // UNIQUE INDEX CHỦ CHỐT (Block G3: Idempotency)
  cart_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Cart', required: true },
  email_sequence: { type: Number, required: true },
  recipient_email: { type: String, required: true },
  esp_message_id: { type: String },
  status: { type: String, enum: ['sent', 'failed', 'skipped'], required: true },
  sent_at: { type: Date, default: Date.now },
  error_message: { type: String }
});

module.exports = mongoose.model('EmailSendLog', EmailSendLogSchema);

// Helper chặn Lỗi Duplicate E11000
module.exports.isDuplicateKeyError = (error) => {
    return error && error.name === 'MongoServerError' && error.code === 11000;
};
