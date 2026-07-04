const express = require('express');
const router = express.Router();
const { trackEvent } = require('../controllers/trackingController');

// POST /api/v1/tracking/event
router.post('/event', trackEvent);

module.exports = router;
