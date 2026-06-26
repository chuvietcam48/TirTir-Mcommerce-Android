const express = require('express');
const router = express.Router();
const { streamChat } = require('../controllers/chatController');

router.get('/stream', streamChat);
router.post('/stream', streamChat); // In case FE uses POST

module.exports = router;
