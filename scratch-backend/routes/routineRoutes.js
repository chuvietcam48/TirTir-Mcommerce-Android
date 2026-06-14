const express = require('express');
const router = express.Router();
const { saveRoutine, getRecommendation } = require('../controllers/routineController');
const { protect } = require('../middleware/authMiddleware');

// Base URL: /api/v1/routines
router.post('/save', protect, saveRoutine);
router.get('/recommendation', protect, getRecommendation);

module.exports = router;
