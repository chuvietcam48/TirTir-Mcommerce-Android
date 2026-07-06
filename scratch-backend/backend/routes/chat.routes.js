const express = require('express');
const router = express.Router();
const chatController = require('../controllers/chat.controller');
const { protect, optionalProtect } = require('../middlewares/auth');

// ── Public endpoints (no auth required) ──────────────────────────────────────
router.get('/config',              chatController.getChatConfig);
router.get('/suggested-questions', chatController.getSuggestedQuestions);
router.get('/categories',          chatController.getChatCategories);

// ── Messaging — optional auth: guests can chat, but only logged-in users persist ──
router.post('/', optionalProtect, chatController.chatWithBot);

// ── Auth-required endpoints ───────────────────────────────────────────────────
router.get('/history',    protect, chatController.getChatHistory);
router.post('/handoff',   protect, chatController.postHandoff);
router.delete('/history', protect, chatController.clearChatHistory);

module.exports = router;
