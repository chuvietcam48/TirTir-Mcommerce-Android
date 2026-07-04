const express = require('express');
const router = express.Router();
const { streamChat, getChatHistory } = require('../controllers/chatController');
const { protect } = require('../middleware/authMiddleware');

router.get('/stream', streamChat);
router.post('/stream', streamChat); // FE uses POST with Accept: text/event-stream

// GET /api/v1/chat/history — fetch conversation history from Firestore
router.get('/history', protect, getChatHistory);

module.exports = router;
