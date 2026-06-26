const express = require('express');
const router = express.Router();
const { analyzeIngredients, analyzeFace } = require('../controllers/aiController');
const { protect } = require('../middleware/authMiddleware');

router.post('/analyze-ingredients', analyzeIngredients);
router.post('/analyze-face', analyzeFace);

module.exports = router;
