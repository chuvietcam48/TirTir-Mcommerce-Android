const { processChatbotMessage } = require('../services/geminiService');
const admin = require('firebase-admin');

// POST /api/v1/chat and POST /api/v1/chat/stream
// Android ChatRepository posts to CHAT_URL = BASE_URL + "api/v1/chat"
exports.streamChat = async (req, res) => {
  try {
    const message = (req.body && req.body.message) || req.query.message;
    const productId = (req.body && req.body.productId) || req.query.productId;
    const userId = req.user ? (req.user.id || req.user._id) : null;

    console.log(`[CHAT] Incoming message. userId=${userId}, message="${message ? message.substring(0, 60) : 'EMPTY'}"`);

    if (!message || !message.trim()) {
      console.warn('[CHAT] Empty message received');
      if (!res.headersSent) {
        res.writeHead(200, {
          'Content-Type': 'text/event-stream',
          'Cache-Control': 'no-cache',
        });
      }
      const errorPayload = JSON.stringify({ message: 'Please type a message to get a response.' });
      res.write(`event: error\ndata: ${errorPayload}\n\n`);
      return res.end();
    }

    const result = await processChatbotMessage({ userId, message: message.trim(), productId });
    const replyText = result.reply;

    console.log(`[CHAT] Reply ready, length=${replyText ? replyText.length : 0}`);

    // Respond with SSE format so Android ChatRepository SSE parser can handle it
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no', // disable nginx buffering for SSE
    });

    // Stream each word as a "chunk" event that Android ChatRepository can parse
    const words = replyText.split(' ');
    for (const word of words) {
      if (word.trim()) {
        const chunkPayload = JSON.stringify({ text: word + ' ' });
        res.write(`event: chunk\ndata: ${chunkPayload}\n\n`);
        await new Promise(r => setTimeout(r, 20));
      }
    }

    // Send final "done" event with full message
    const donePayload = JSON.stringify({
      message: replyText,
      data: { recommendations: [] }
    });
    res.write(`event: done\ndata: ${donePayload}\n\n`);
    res.end();
  } catch (err) {
    console.error('[CHAT] Unexpected error:', err.message, err.stack ? err.stack.substring(0, 500) : '');
    if (!res.headersSent) {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      });
    }
    // Return a helpful message even on error, not a generic failure
    const fallbackMsg = 'Hi! I\'m your TirTir Beauty Advisor. I can help with skincare routines, product recommendations, and ingredient advice. What would you like to know? 🌸';
    const words = fallbackMsg.split(' ');
    for (const word of words) {
      if (word.trim()) {
        const chunkPayload = JSON.stringify({ text: word + ' ' });
        res.write(`event: chunk\ndata: ${chunkPayload}\n\n`);
      }
    }
    const donePayload = JSON.stringify({ message: fallbackMsg, data: { recommendations: [] } });
    res.write(`event: done\ndata: ${donePayload}\n\n`);
    res.end();
  }
};

// GET /api/v1/chat/history — reads logged-in user's Firestore chat_history
exports.getChatHistory = async (req, res) => {
  try {
    const userId = req.user ? String(req.user.id || req.user._id) : null;
    if (!userId) return res.status(401).json({ success: false, message: 'Unauthorized' });

    const db = admin.firestore();
    const snapshot = await db
      .collection('users')
      .doc(userId)
      .collection('chat_history')
      .orderBy('createdAt', 'asc')
      .limit(100)
      .get();

    const messages = snapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      createdAt: doc.data().createdAt ? doc.data().createdAt.toDate().toISOString() : null
    }));

    return res.status(200).json({ success: true, data: messages });
  } catch (err) {
    console.error('getChatHistory error:', err);
    return res.status(500).json({ success: false, data: [] });
  }
};
