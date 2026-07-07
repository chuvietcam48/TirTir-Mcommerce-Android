const admin = require('firebase-admin');
const User = require('../models/User');
const Product = require('../models/Product');
const { buildChatbotPrompt } = require('../services/promptBuilder');
const { generateGeminiResponse } = require('../services/geminiService');
const mongoose = require('mongoose');
const { parseAndValidateDetectedSkinType, saveChatHistory } = require('../services/chatHistoryService');

// Simple intent detector for "recommend product"
function isRecommendIntent(message) {
  const lower = message.toLowerCase();
  const keywords = [
    'recommend', 'gợi ý', 'tư vấn', 'sản phẩm nào', 'nên dùng', 'phù hợp',
    'suggest', 'product', 'sản phẩm', 'mua gì', 'chọn gì'
  ];
  return keywords.some(kw => lower.includes(kw));
}

// POST /api/chatbot/message and /api/v1/chatbot/message
exports.handleChatbotMessage = async (req, res) => {
  try {
    const { userId, message, productId } = req.body;

    // 1. Validate inputs (allow anonymous)
    if (!message || typeof message !== 'string' || !message.trim()) {
      return res.status(400).json({ success: false, message: 'message là bắt buộc và không được để trống.' });
    }

    // 2. Load User Context (default profile if anonymous)
    let userProfile = {
      skinType: 'combination',
      knownAllergies: [],
      loyaltyTier: 'Silver'
    };

    if (userId) {
      try {
        const db = admin.firestore();
        const userDoc = await db.collection('users').doc(String(userId)).get();
        if (userDoc.exists) {
          userProfile = { ...userProfile, ...userDoc.data() };
        }
      } catch (fsErr) {
        console.error('[CHATBOT_CTRL] Firestore user fetch error:', fsErr.message);
      }

      // Fallback MongoDB User lookup
      if (!userProfile.firstName) {
        try {
          const mongoUser = await User.findById(userId).select('gender birthDate email firstName lastName skinType role');
          if (mongoUser) {
            userProfile.skinType = mongoUser.skinType || userProfile.skinType;
          }
        } catch (dbErr) {
          // ignore Mongo ID format invalid error if string uid
        }
      }
    }

    // 3. Load specific Product Context if productId provided
    let productContext = null;
    if (productId) {
      try {
        const query = mongoose.Types.ObjectId.isValid(productId) 
            ? { _id: productId } 
            : { Product_ID: productId };
        const product = await Product.findOne(query).lean();

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

    // 4. If user is asking for recommendations, fetch real product catalog & inject into context
    let productCatalogContext = '';
    if (!productId && isRecommendIntent(message.trim())) {
      try {
        const skinType = (userProfile.skinType || 'combination').toLowerCase();

        const products = await Product.find({
          Status: { $ne: 'inactive' },
          Stock_Quantity: { $gt: 0 }
        })
          .select('Name Category Price Sale_Price Skin_Type_Target Main_Concern Description_Short Product_ID')
          .limit(30)
          .lean();

        if (products.length > 0) {
          // Sort: exact skin type matches first
          const sorted = products.sort((a, b) => {
            const aMatch = (a.Skin_Type_Target || '').toLowerCase().includes(skinType) ? 0 : 1;
            const bMatch = (b.Skin_Type_Target || '').toLowerCase().includes(skinType) ? 0 : 1;
            return aMatch - bMatch;
          });

          const top10 = sorted.slice(0, 10);
          const productList = top10.map(p => {
            const price = p.Sale_Price > 0 ? p.Sale_Price : p.Price;
            return `- ${p.Name} (${p.Category || 'Skincare'}) — ${price?.toLocaleString('vi-VN')}đ — Phù hợp: ${p.Skin_Type_Target || 'Mọi loại da'}`;
          }).join('\n');

          productCatalogContext = `\n\nDanh mục sản phẩm TirTir hiện có (PHẢI gợi ý từ danh sách này, dùng đúng tên thật):\n${productList}`;
        }
      } catch (catErr) {
        console.error('[CHATBOT_CTRL] Catalog fetch error:', catErr.message);
      }
    }

    // 5. Build Dynamic Prompt
    const { systemInstruction, userMessage } = buildChatbotPrompt({
      userProfile,
      productContext,
      productCatalogContext,
      message: message.trim()
    });

    // 6. Call Gemini AI
    const rawResponse = await generateGeminiResponse(systemInstruction, userMessage);

    // 7. Parse and validate detected skin type
    const { cleanReply, detectedSkinType } = parseAndValidateDetectedSkinType(rawResponse);

    // 8. Save conversation to Firestore (only if logged in)
    if (userId) {
      await saveChatHistory({
        userId,
        userMessage: message.trim(),
        botMessage: cleanReply,
        productId: productId || null,
        productName: productContext ? productContext.productName : null,
        skinType: userProfile.skinType || 'combination',
        detectedSkinType
      });
    }

    // 9. Return response
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
