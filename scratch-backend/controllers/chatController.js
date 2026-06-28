const { processChatbotMessage } = require('../services/geminiService');

// POST or GET /api/v1/chat/stream or /api/v1/chat/message
exports.streamChat = async (req, res) => {
  try {
    const message = req.body.message || req.query.message || 'Xin chào';
    const productId = req.body.productId || req.query.productId;
    const userId = req.user ? req.user.id : null;

    const isSSE = req.headers.accept && req.headers.accept.includes('text/event-stream');

    const result = await processChatbotMessage({ userId, message, productId });
    const replyText = result.reply;

    if (isSSE) {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
      });

      const words = replyText.split(' ');
      for (const word of words) {
        res.write(`data: ${word} \n\n`);
        await new Promise(r => setTimeout(r, 30)); // Natural fluid typing effect
      }
      res.write(`data: [DONE]\n\n`);
      res.end();
    } else {
      res.status(200).json({
        success: true,
        data: {
          reply: replyText,
          skinType: result.skinType
        }
      });
    }
  } catch (err) {
    console.error('Chatbot error:', err);
    if (!res.headersSent) {
      res.status(500).json({ success: false, message: err.message || 'Server error in chatbot' });
    }
  }
};
