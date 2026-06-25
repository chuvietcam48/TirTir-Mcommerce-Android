const express = require('express');
const router = express.Router();
const { protect, optionalProtect } = require('../middlewares/auth');
const {
    saveRoutine,
    getCommunityRoutines,
    likeRoutine,
    applyRoutine,
    suggestRoutine
} = require('../controllers/routine.controller');

// Matches /api/v1/routines/...
router.post('/save', optionalProtect, saveRoutine);
router.get('/community', optionalProtect, getCommunityRoutines);
router.post('/:id/like', optionalProtect, likeRoutine);
router.post('/:id/apply', protect, applyRoutine);

// Matches /api/v1/routine/...
router.get('/suggest', optionalProtect, suggestRoutine);

module.exports = router;
