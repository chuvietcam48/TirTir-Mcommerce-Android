const express = require('express');
const router = express.Router();
const analyticsController = require('../controllers/analytics.controller');

router.post('/view', analyticsController.trackView);
router.post('/add-to-cart', analyticsController.trackAddToCart);
router.post('/chat', analyticsController.trackChat);

module.exports = router;
