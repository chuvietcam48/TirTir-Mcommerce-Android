const express = require('express');
const router = express.Router();
const { streamChat, getChatHistory } = require('../controllers/chatController');
const { protect, optionalProtect } = require('../middleware/authMiddleware');

// Use optionalProtect so req.user is populated when a valid token is provided,
// but the endpoint still works for unauthenticated requests (guest users).
router.get('/stream', optionalProtect, streamChat);
router.post('/stream', optionalProtect, streamChat); // FE uses POST with Accept: text/event-stream

// Also handle POST directly at /api/v1/chat (without /stream suffix) since
// Android ChatRepository posts to ApiConfig.CHAT_URL = BASE_URL + "api/v1/chat"
router.post('/', optionalProtect, streamChat);

// GET /api/v1/chat/history — fetch conversation history from Firestore
router.get('/history', protect, getChatHistory);

module.exports = router;
