const express = require('express');
const router = express.Router();
const chatController = require('../controllers/chat.controller');
const { protect, optionalProtect } = require('../middlewares/auth');

// POST /api/v1/chat
// optionalProtect: guests pass through freely; logged-in users get req.user
// attached so the controller can auto-persist to MongoDB.
router.post('/', optionalProtect, chatController.chatWithBot);

// GET  /api/v1/chat/history  — JWT required, returns user's message array
router.get('/history', protect, chatController.getChatHistory);

// DELETE /api/v1/chat/history — JWT required, wipes the user's history
router.delete('/history', protect, chatController.clearChatHistory);

module.exports = router;