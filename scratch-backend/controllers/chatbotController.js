const admin = require('firebase-admin');
const User = require('../models/User');
const Product = require('../models/Product');
const { buildChatbotPrompt } = require('../services/promptBuilder');
const { generateGeminiResponse } = require('../services/geminiService');
const { parseAndValidateDetectedSkinType, saveChatHistory } = require('../services/chatHistoryService');

// POST /api/chatbot/message and /api/v1/chatbot/message
exports.handleChatbotMessage = async (req, res) => {
  try {
    const { userId, message, productId } = req.body;

    // 1. Validate inputs
    if (!userId) {
      return res.status(400).json({ success: false, message: 'userId là bắt buộc.' });
    }
    if (!message || typeof message !== 'string' || !message.trim()) {
      return res.status(400).json({ success: false, message: 'message là bắt buộc và không được để trống.' });
    }

    // 2. Load User Context from Firestore users/{uid} (fallback MongoDB)
    let userProfile = null;
    try {
      const db = admin.firestore();
      const userDoc = await db.collection('users').doc(String(userId)).get();
      if (userDoc.exists) {
        userProfile = userDoc.data();
      }
    } catch (fsErr) {
      console.error('[CHATBOT_CTRL] Firestore user fetch error:', fsErr.message);
    }

    // Fallback MongoDB User lookup if Firestore record not found or incomplete
    if (!userProfile) {
      try {
        const mongoUser = await User.findById(userId).select('gender birthDate email firstName lastName role');
        if (mongoUser) {
          userProfile = {
            skinType: 'Chưa xác định',
            knownAllergies: [],
            loyaltyTier: 'Silver'
          };
        }
      } catch (dbErr) {
        // ignore Mongo ID format invalid error if string uid
      }
    }

    if (!userProfile) {
      return res.status(404).json({ success: false, message: 'Không tìm thấy hồ sơ người dùng.' });
    }

    // 3. Load Product Context from MongoDB if productId exists
    let productContext = null;
    if (productId) {
      try {
        const product = await Product.findOne({
          $or: [{ Product_ID: productId }, { _id: productId }]
        }).lean();

        if (product) {
          productContext = {
            productName: product.Name,
            brand: 'TirTir',
            ingredients: product.Key_Ingredients || product.Full_Description || 'Không rõ',
            category: product.Category,
            skinTypeTarget: product.Skin_Type_Target,
            warnings: null
          };
        }
      } catch (prodErr) {
        console.error('[CHATBOT_CTRL] Product fetch error:', prodErr.message);
      }
    }

    // 4. Build Dynamic Prompt
    const { systemInstruction, userMessage } = buildChatbotPrompt({
      userProfile,
      productContext,
      message: message.trim()
    });

    // 5. Call Gemini AI (with timeout & retry handled in service)
    const rawResponse = await generateGeminiResponse(systemInstruction, userMessage);

    // 6. Parse and validate detected skin type
    const { cleanReply, detectedSkinType } = parseAndValidateDetectedSkinType(rawResponse);

    // 7. Save conversation to Firestore chat_history
    await saveChatHistory({
      userId,
      userMessage: message.trim(),
      botMessage: cleanReply,
      productId: productId || null,
      productName: productContext ? productContext.productName : null,
      skinType: userProfile.skinType || 'Chưa xác định',
      detectedSkinType
    });

    // 8. Return response
    return res.status(200).json({
      success: true,
      data: {
        reply: cleanReply,
        detectedSkinType
      }
    });

  } catch (err) {
    console.error('[CHATBOT_CTRL] Unexpected error:', err);
    return res.status(500).json({
      success: false,
      message: 'Lỗi máy chủ trong quá trình xử lý tin nhắn chatbot.'
    });
  }
};
