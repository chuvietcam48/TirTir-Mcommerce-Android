const express = require('express');
const router = express.Router();
const { getChatbotMessage } = require('../controllers/chatbot.controller');
const { optionalProtect } = require('../middlewares/auth');

// POST /api/v1/chatbot/message
router.post('/message', optionalProtect, getChatbotMessage);

module.exports = router;
