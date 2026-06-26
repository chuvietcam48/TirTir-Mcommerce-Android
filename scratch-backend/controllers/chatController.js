const express = require('express');

// GET or POST /api/v1/chat/stream
exports.streamChat = async (req, res) => {
  // Set headers for Server-Sent Events (SSE)
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
  });

  const message = "Xin chào! Mình là trợ lý ảo của TirTir. Rất vui được hỗ trợ bạn trong việc chăm sóc da và lựa chọn sản phẩm phù hợp. Bạn cần mình giúp gì nào?";
  const words = message.split(' ');
  let i = 0;

  const timer = setInterval(() => {
    if (i < words.length) {
      res.write(`data: ${words[i]} \n\n`);
      i++;
    } else {
      res.write(`data: [DONE]\n\n`);
      clearInterval(timer);
      res.end();
    }
  }, 100);

  req.on('close', () => {
    clearInterval(timer);
  });
};
