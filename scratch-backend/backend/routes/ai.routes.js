const express = require('express');
const router = express.Router();
const aiController = require('../controllers/ai.controller');
const { protect, optionalProtect } = require('../middlewares/auth');
const { aiScanLimiter } = require('../middlewares/rateLimit');

/**
 * @route   POST /api/ai/analyze-face
 * @desc    Analyze skin using Python AI
 * @access  Public (Optional Auth for history)
 */
router.post('/analyze-face', aiScanLimiter, optionalProtect, aiController.analyzeFace);
router.post('/recommend-routine', aiScanLimiter, optionalProtect, aiController.recommendRoutine);
router.get('/latest-profile', protect, aiController.getLatestProfile);
router.post('/save-result', protect, aiController.saveResult);
router.get('/history', protect, aiController.getHistory);

// Legacy/Alternative
router.post('/analyze-skin', aiScanLimiter, aiController.analyzeSkin);
router.get('/health', aiController.healthCheck);
router.post('/routine-feedback', protect, aiController.submitRoutineFeedback);

module.exports = router;
