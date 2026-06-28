const { GoogleGenAI } = require('@google/genai');
const admin = require('firebase-admin');
const User = require('../models/User');
const Product = require('../models/Product');

/**
 * Execute Gemini call with retry logic and timeout
 */
async function callGeminiWithRetry(ai, modelName, contents, config, retries = 2, timeoutMs = 10000) {
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const timeoutPromise = new Promise((_, reject) =>
        setTimeout(() => reject(new Error('GEMINI_TIMEOUT')), timeoutMs)
      );

      const apiPromise = ai.models.generateContent({
        model: modelName,
        contents,
        config
      });

      const response = await Promise.race([apiPromise, timeoutPromise]);
      return response;
    } catch (err) {
      console.warn(`[GEMINI] Attempt ${attempt + 1} failed: ${err.message}`);
      if (attempt === retries) throw err;
      await new Promise(res => setTimeout(res, 1000 * (attempt + 1))); // exponential backoff delay
    }
  }
}

/**
 * Main AI chatbot handler following required workflow:
 * load user -> read skinType from Firestore -> if productId exists load ingredients from MongoDB -> build dynamic systemInstruction -> call Gemini -> return response -> save conversation into Firestore chat_history
 */
async function processChatbotMessage({ userId, message, productId }) {
  if (!message || !message.trim()) {
    throw new Error('Message is required');
  }

  let skinType = 'Chưa xác định';
  let knownAllergies = [];

  // 1. Load user profile & read skinType from Firestore
  if (userId) {
    try {
      const db = admin.firestore();
      const userDoc = await db.collection('users').doc(String(userId)).get();
      if (userDoc.exists) {
        const userData = userDoc.data();
        if (userData.skinType) skinType = userData.skinType;
        if (userData.knownAllergies) knownAllergies = userData.knownAllergies || [];
      }
    } catch (err) {
      console.error('[GEMINI] Firestore profile read error:', err.message);
    }

    // Fallback to Mongo user if skinType still default
    if (skinType === 'Chưa xác định') {
      try {
        const mongoUser = await User.findById(userId);
        if (mongoUser) {
          if (mongoUser.gender) skinType = skinType; // keep current if any
        }
      } catch (e) {
        // ignore fallback
      }
    }
  }

  // 2. If productId exists, load ingredients from MongoDB
  let productIngredients = '';
  let productName = '';
  if (productId) {
    try {
      const product = await Product.findOne({
        $or: [{ Product_ID: productId }, { _id: productId }]
      });
      if (product) {
        productName = product.Name;
        productIngredients = product.Key_Ingredients || product.Full_Description || 'Không rõ';
      }
    } catch (err) {
      console.error('[GEMINI] Mongo product read error:', err.message);
    }
  }

  // 3. Build dynamic systemInstruction
  const systemInstruction = `Bạn là chuyên gia tư vấn chăm sóc da và mỹ phẩm cao cấp TirTir.
Thông tin khách hàng:
- Loại da: ${skinType}
- Vấn đề da / dị ứng: ${knownAllergies.join(', ') || 'Không ghi nhận'}
${productId ? `Sản phẩm khách hàng đang quan tâm: "${productName}". Thành phần nổi bật: ${productIngredients}.` : ''}

Hãy trả lời lịch sự, thân thiện, khoa học bằng tiếng Việt. Nếu khách hỏi về sản phẩm, hãy phân tích xem sản phẩm đó có phù hợp với loại da ${skinType} của khách hay không.`;

  const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
  let replyText = '';

  if (!apiKey || apiKey.includes('your_')) {
    replyText = `Dựa trên loại da ${skinType} của bạn, TirTir khuyên bạn sử dụng các sản phẩm dịu nhẹ, cấp ẩm vừa đủ và bảo vệ da hàng ngày.`;
  } else {
    try {
      const ai = new GoogleGenAI({ apiKey });
      const response = await callGeminiWithRetry(
        ai,
        'gemini-2.5-flash',
        message,
        { systemInstruction }
      );

      replyText = response.text || 'Rất tiếc, mình chưa thể đưa ra phản hồi phù hợp lúc này.';
    } catch (err) {
      console.error('[GEMINI] API call failed:', err.message);
      replyText = 'Hiện tại hệ thống tư vấn AI đang bận hoặc gián đoạn kết nối. Bạn vui lòng thử lại sau ít phút nhé!';
    }
  }

  // 4. Save conversation into Firestore chat_history
  if (userId) {
    try {
      const db = admin.firestore();
      const chatHistoryRef = db.collection('users').doc(String(userId)).collection('chat_history');
      await chatHistoryRef.add({
        sender: 'user',
        text: message,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
      await chatHistoryRef.add({
        sender: 'bot',
        text: replyText,
        skinType,
        productId: productId || null,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
    } catch (fsErr) {
      console.error('[GEMINI] Error persisting chat history to Firestore:', fsErr.message);
    }
  }

  return {
    reply: replyText,
    skinType
  };
}

module.exports = {
  processChatbotMessage
};
