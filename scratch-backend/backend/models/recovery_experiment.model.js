const mongoose = require('mongoose');

const ExperimentSchema = new mongoose.Schema({
    name: { type: String, required: true, unique: true },
    status: { type: String, enum: ['draft', 'running', 'paused', 'completed'], default: 'draft' },
    variants: [{ 
      name: String, 
      weight: Number, 
      config: mongoose.Schema.Types.Mixed 
    }],
    primary_metric: String,
    start_date: Date,
    end_date: Date,
});

module.exports = mongoose.model('RecoveryExperiment', ExperimentSchema);
