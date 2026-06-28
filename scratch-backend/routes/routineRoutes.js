const express = require('express');
const router = express.Router();
const { saveRoutine, getCommunityRoutines, likeRoutine, applyRoutine, getRecommendation } = require('../controllers/routineController');
const { protect } = require('../middleware/authMiddleware');

// Base URL: /api/v1/routines
router.post('/save', protect, saveRoutine);
router.get('/community', getCommunityRoutines);
router.post('/:id/like', protect, likeRoutine);
router.post('/:id/apply', protect, applyRoutine);
router.get('/suggestion', protect, getRecommendation);
router.get('/recommendation', protect, getRecommendation);

module.exports = router;
