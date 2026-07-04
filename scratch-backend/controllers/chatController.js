const { processChatbotMessage } = require('../services/geminiService');
const admin = require('firebase-admin');

// POST /api/v1/chat/stream
exports.streamChat = async (req, res) => {
  try {
    const message = req.body.message || req.query.message || 'Xin chào';
    const productId = req.body.productId || req.query.productId;
    const userId = req.user ? req.user.id : null;

    const result = await processChatbotMessage({ userId, message, productId });
    const replyText = result.reply;

    // Always respond with SSE format since Android client sends Accept: text/event-stream
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no', // disable nginx buffering for SSE
    });

    // Stream each word as a "chunk" event that Android ChatRepository can parse
    const words = replyText.split(' ');
    for (const word of words) {
      const chunkPayload = JSON.stringify({ text: word + ' ' });
      res.write(`event: chunk\ndata: ${chunkPayload}\n\n`);
      await new Promise(r => setTimeout(r, 30));
    }

    // Send final "done" event with full message and any suggestions
    const donePayload = JSON.stringify({
      message: replyText,
      data: { recommendations: [] }
    });
    res.write(`event: done\ndata: ${donePayload}\n\n`);
    res.end();
  } catch (err) {
    console.error('Chatbot error:', err);
    if (!res.headersSent) {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      });
    }
    const errorPayload = JSON.stringify({ message: 'The advisor is temporarily unavailable. Please try again.' });
    res.write(`event: error\ndata: ${errorPayload}\n\n`);
    res.end();
  }
};

// GET /api/v1/chat/history — reads logged-in user's Firestore chat_history
exports.getChatHistory = async (req, res) => {
  try {
    const userId = req.user ? String(req.user.id) : null;
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
