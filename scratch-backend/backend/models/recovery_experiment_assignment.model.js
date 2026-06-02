const mongoose = require('mongoose');

const AssignmentSchema = new mongoose.Schema({
    cart_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Cart', required: true },
    experiment_id: { type: mongoose.Schema.Types.ObjectId, ref: 'RecoveryExperiment', required: true },
    variant_name: { type: String, required: true },
    assigned_at: { type: Date, default: Date.now }
});

// INDEX BẮT BUỘC: Mỗi cart chỉ được gán 1 lần cho 1 thí nghiệm
AssignmentSchema.index({ cart_id: 1, experiment_id: 1 }, { unique: true });

module.exports = mongoose.model('RecoveryExperimentAssignment', AssignmentSchema);
