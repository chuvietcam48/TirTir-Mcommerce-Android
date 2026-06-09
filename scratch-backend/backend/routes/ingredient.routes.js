const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const { saveScanHistory, getScanHistory } = require('../controllers/ingredient.controller');

// POST /api/v1/ingredient/scan-history — save a scan record to Firestore
router.post('/scan-history', protect, saveScanHistory);

// GET  /api/v1/ingredient/scan-history?userId=  — list scan history sorted desc
router.get('/scan-history', protect, getScanHistory);

module.exports = router;
