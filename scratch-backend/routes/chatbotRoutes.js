const express = require('express');
const router = express.Router();
const { handleChatbotMessage } = require('../controllers/chatbotController');

// POST /api/chatbot/message and /api/v1/chatbot/message
router.post('/message', handleChatbotMessage);

module.exports = router;
