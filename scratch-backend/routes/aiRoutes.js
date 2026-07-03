const express = require('express');
const router = express.Router();
const { analyzeIngredients, analyzeFace, recommendRoutine } = require('../controllers/aiController');
const { protect, optionalProtect } = require('../middleware/authMiddleware');

router.post('/analyze-ingredients', analyzeIngredients);
router.post('/analyze-face', analyzeFace);
router.post('/recommend-routine', optionalProtect, recommendRoutine);

module.exports = router;
