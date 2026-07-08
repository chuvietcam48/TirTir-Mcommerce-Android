const { GoogleGenAI } = require('@google/genai');
const admin = require('firebase-admin');
const mongoose = require('mongoose');
const User = require('../models/User');
const Product = require('../models/Product');
const { buildChatbotPrompt } = require('./promptBuilder');

/**
 * Execute Gemini call with retry logic and timeout
 */
async function callGeminiWithRetry(ai, modelName, contents, config, retries = 2, timeoutMs = 25000) {
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const timeoutPromise = new Promise((_, reject) =>
        setTimeout(() => reject(new Error('GEMINI_TIMEOUT')), timeoutMs)
      );

      const apiPromise = ai.models.generateContent({
        model: modelName.trim(),
        contents,
        ...config
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

  let skinType = 'combination';
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
    if (skinType === 'combination') {
      try {
        const mongoUser = await User.findById(userId);
        if (mongoUser && mongoUser.skinType) {
          skinType = mongoUser.skinType;
        }
      } catch (e) {
        // ignore fallback
      }
    }
  }

  // 2. If productId exists, load ingredients from MongoDB
  let productContext = null;
  let productName = null;
  if (productId) {
    try {
      const query = mongoose.Types.ObjectId.isValid(productId) 
          ? { _id: productId } 
          : { Product_ID: productId };
      const product = await Product.findOne(query);
      
      if (product) {
        productName = product.Name;
        productContext = {
          productName: product.Name,
          brand: 'TirTir',
          ingredients: product.Key_Ingredients || product.Full_Description || 'Không rõ',
          category: product.Category,
          skinTypeTarget: product.Skin_Type_Target,
          warnings: null
        };
      }
    } catch (err) {
      console.error('[GEMINI] Mongo product read error:', err.message);
    }
  }

  // 2.5 Fetch product catalog to recommend from (with ingredients)
  let productCatalogContext = null;
  try {
    const products = await Product.find({
      Status: { $ne: 'inactive' },
      Stock_Quantity: { $gt: 0 }
    })
      .select('Name Category Price Sale_Price Skin_Type_Target Key_Ingredients Product_ID')
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
        const ingredients = p.Key_Ingredients || 'Không rõ thành phần';
        return `- Tên: ${p.Name}\n  Phân loại: ${p.Category || 'Skincare'}\n  Loại da khuyên dùng: ${p.Skin_Type_Target || 'Mọi loại da'}\n  Thành phần chính: ${ingredients}`;
      }).join('\n\n');

      productCatalogContext = `\n\n[DANH SÁCH SẢN PHẨM DATABASE HIỆN CÓ]:\nBẠN BẮT BUỘC CHỈ ĐƯỢC PHÉP RECOMMEND TỪ DANH SÁCH DƯỚI ĐÂY. KHÔNG ĐƯỢC BỊA RA SẢN PHẨM BÊN NGOÀI. KHI GỢI Ý, PHẢI DỰA VÀO "THÀNH PHẦN CHÍNH" ĐỂ GIẢI THÍCH LÝ DO TẠI SAO PHÙ HỢP VỚI LOẠI DA CỦA KHÁCH:\n\n${productList}`;
    }
  } catch (err) {
    console.error('[GEMINI] Catalog fetch error:', err.message);
  }

  // 3. Build dynamic systemInstruction
  const { systemInstruction } = buildChatbotPrompt({
    userProfile: { skinType, knownAllergies },
    productContext,
    productCatalogContext,
    message: message.trim()
  });

  const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
  let replyText = '';

  console.log(`[GEMINI] Processing message. userId=${userId}, apiKeySet=${!!(apiKey && !apiKey.includes('your_') && apiKey.trim() !== '')}`);

  if (!apiKey || apiKey.includes('your_') || apiKey.trim() === '') {
    console.warn('[GEMINI] API key not configured — using smart fallback response');
    replyText = generateSmartFallback(message.trim(), skinType, productName);
  } else {
    try {
      const ai = new GoogleGenAI({ apiKey, apiVersion: 'v1' });
      // contents must be an array of { role, parts } objects
      const contents = [
        {
          role: 'user',
          parts: [{ text: message.trim() }]
        }
      ];
      console.log(`[GEMINI] Calling Gemini API with model gemini-2.5-flash`);
      const response = await callGeminiWithRetry(
        ai,
        'gemini-2.5-flash',
        contents,
        { systemInstruction }
      );

      // response.text is a getter property in @google/genai v2
      const rawText = response.text;
      if (rawText && rawText.trim()) {
        replyText = rawText.trim();
        console.log(`[GEMINI] API response received, length=${replyText.length}`);
      } else {
        console.warn('[GEMINI] Empty text in response, using fallback');
        replyText = generateSmartFallback(message.trim(), skinType, productName);
      }
    } catch (err) {
      console.error('[GEMINI] API call failed:', err.message, err.stack ? err.stack.substring(0, 500) : '');
      replyText = generateSmartFallback(message.trim(), skinType, productName);
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

/**
 * Generate a smart contextual fallback response when Gemini API is unavailable.
 * Returns helpful, relevant content in Vietnamese based on keywords.
 */
function generateSmartFallback(message, skinType, productName) {
  const msgLower = message.toLowerCase();

  // Keyword: Product mentioned in context
  if (productName && (msgLower.includes('sản phẩm này') || msgLower.includes('dùng sao') || msgLower.includes('tốt không') || msgLower.includes('hợp') || msgLower.includes('phù hợp'))) {
    return `${productName} là một sản phẩm rất tuyệt vời của TirTir! Đối với làn da ${skinType} của bạn, sản phẩm này hoàn toàn có thể sử dụng được. Tuy nhiên, bạn nên thoa thử một vùng nhỏ trước nhé. Bạn có cần mình hướng dẫn cách kết hợp sản phẩm này vào chu trình dưỡng da không?`;
  }

  // Keyword: Recommend, gợi ý, tư vấn sản phẩm
  if (msgLower.includes('recommend') || msgLower.includes('gợi ý') || msgLower.includes('tư vấn') || msgLower.includes('sản phẩm nào') || msgLower.includes('nên dùng')) {
    let rec = '';
    if (skinType.toLowerCase().includes('oily') || skinType.toLowerCase().includes('dầu')) {
      rec = 'Sữa rửa mặt tạo bọt kiềm dầu và Toner làm dịu da';
    } else if (skinType.toLowerCase().includes('dry') || skinType.toLowerCase().includes('khô')) {
      rec = 'Kem dưỡng ẩm sâu TirTir Ceramic Cream và Serum cấp nước';
    } else {
      rec = 'TirTir Milk Skin Toner và kem dưỡng ẩm cơ bản';
    }
    return `Chào bạn! Đối với làn da ${skinType}, mình khuyên bạn nên thử ${rec}. Những sản phẩm này rất phù hợp với tình trạng da của bạn. Bạn muốn tìm hiểu kỹ hơn về sản phẩm nào?`;
  }

  // Keyword: Sunscreen, chống nắng, spf
  if (msgLower.includes('sunscreen') || msgLower.includes('spf') || msgLower.includes('uv') || msgLower.includes('chống nắng')) {
    return `Kem chống nắng là bước cực kỳ quan trọng! Với da ${skinType}, bạn nên chọn SPF 30+ quang phổ rộng. Thoa vào bước cuối cùng của buổi sáng. TirTir Hydro UV Shield là một lựa chọn tuyệt vời, mỏng nhẹ và không gây bết dính. ☀️`;
  }

  // Keyword: Routine, chu trình, các bước
  if (msgLower.includes('routine') || msgLower.includes('steps') || msgLower.includes('chu trình') || msgLower.includes('các bước') || msgLower.includes('skincare')) {
    return `Một chu trình chăm sóc chuẩn cho da ${skinType}: 1️⃣ Tẩy trang & Sữa rửa mặt → 2️⃣ Toner cấp ẩm → 3️⃣ Serum đặc trị → 4️⃣ Kem dưỡng → 5️⃣ Kem chống nắng (buổi sáng). TirTir có đủ các dòng sản phẩm cho từng bước, bạn cần mình gợi ý bước nào không?`;
  }

  // Keyword: Ingredients, thành phần, mụn, thâm
  if (msgLower.includes('ingredient') || msgLower.includes('thành phần') || msgLower.includes('mụn') || msgLower.includes('thâm') || msgLower.includes('niacinamide')) {
    return `Về vấn đề thành phần: Hyaluronic Acid giúp cấp nước, Niacinamide giúp giảm thâm và thu nhỏ lỗ chân lông, còn BHA rất tốt cho da mụn. Đối với da ${skinType}, bạn nên ưu tiên các thành phần dịu nhẹ, phục hồi hàng rào bảo vệ da nhé!`;
  }

  // Keyword: Order, đơn hàng, giao hàng
  if (msgLower.includes('order') || msgLower.includes('shipping') || msgLower.includes('đơn hàng') || msgLower.includes('giao hàng') || msgLower.includes('vận chuyển')) {
    return `Để kiểm tra đơn hàng và vận chuyển, bạn vui lòng xem trong mục "Lịch sử đơn hàng" trên app nhé. Nếu cần hỗ trợ thêm, đội ngũ CSKH của TirTir luôn sẵn sàng giúp đỡ bạn! 💌`;
  }

  // Keyword: Skin type, loại da
  if (msgLower.includes('skin type') || msgLower.includes('my skin') || msgLower.includes('da của tôi') || msgLower.includes('loại da')) {
    return `Hồ sơ của bạn cho thấy bạn thuộc tuýp da ${skinType}. Lời khuyên quan trọng: hãy uống đủ nước, sử dụng sản phẩm phù hợp và duy trì chu trình đều đặn. Bạn có muốn nhận gợi ý sản phẩm dành riêng cho da ${skinType} không?`;
  }

  // Keyword: Hello, chào
  if (msgLower.includes('hello') || msgLower.includes('hi') || msgLower.includes('chào')) {
    return `Xin chào! Mình là Chuyên viên tư vấn sắc đẹp của TirTir. 🌸 Mình có thể giúp bạn xây dựng chu trình chăm sóc da, gợi ý sản phẩm cho da ${skinType}, hoặc tư vấn thành phần. Bạn đang quan tâm đến vấn đề gì?`;
  }

  // Catch-all
  return `Xin chào! Mình là Chuyên viên tư vấn của TirTir. 🌸 Mình có thể tư vấn chu trình skincare, gợi ý sản phẩm phù hợp cho da ${skinType}, hoặc giải đáp các thắc mắc về làm đẹp. Mình có thể giúp gì cho bạn hôm nay?`;
}

/**
 * Simplified Gemini response for chatbotController
 */
async function generateGeminiResponse(systemInstruction, userMessage, productName = null) {
  try {
    const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
    if (!apiKey || apiKey.includes('your_') || apiKey.trim() === '') {
      console.warn('[GEMINI] generateGeminiResponse: API key not configured');
      return generateSmartFallback(userMessage, 'combination', null);
    }
    const ai = new GoogleGenAI({ apiKey, apiVersion: 'v1' });
    const contents = [{ role: 'user', parts: [{ text: userMessage }] }];
    const response = await callGeminiWithRetry(ai, 'gemini-2.5-flash', contents, { systemInstruction });
    const text = response.text;
    if (text && text.trim()) return text.trim();
    return generateSmartFallback(userMessage, 'combination', productName);
  } catch (err) {
    console.error('[GEMINI] generateGeminiResponse failed:', err.message);
    return generateSmartFallback(userMessage, 'combination', productName);
  }
}

/**
 * Generate an AI Routine based on skin profile and catalog.
 */
async function generateAiRoutine(skinType, catalog, wantsSunscreen) {
  const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
  const isKeyValid = apiKey && !apiKey.includes('your_') && apiKey.includes('AIza') && apiKey !== 'AIzaSyDummyKeyForLocalTesting123';

  if (!isKeyValid) {
    console.warn('[GEMINI] API key not configured for Routine Generation — using catalog fallback');
    return generateFallbackRoutine(skinType, catalog, wantsSunscreen);
  }

  try {
    const ai = new GoogleGenAI({ apiKey, apiVersion: 'v1' });
    const systemInstruction = `Bạn là một AI phân tích da liễu.
Nhiệm vụ của bạn là lấy các sản phẩm từ danh sách dưới đây để tạo ra một chu trình dưỡng da (skincare routine) phù hợp nhất với da: ${skinType}.
${wantsSunscreen ? 'BẮT BUỘC chèn 1 sản phẩm chống nắng (Sunscreen) vào bước cuối cùng.' : 'KHÔNG đưa vào sản phẩm chống nắng (Sunscreen) và Makeup (Cushion).'}

DANH SÁCH SẢN PHẨM:
${JSON.stringify(catalog.map(p => ({
  id: p.Product_ID,
  name: p.Name,
  category: p.Category,
  price: p.Price
})))}

YÊU CẦU ĐẦU RA (OUTPUT FORMAT):
CHỈ TRẢ VỀ JSON HỢP LỆ (Không có markdown, không có \`\`\`json).
Định dạng JSON là một mảng (array) chứa tối đa 5 bước (steps). Mỗi object gồm:
{
  "step": 1,
  "stepName": "Cleanser",
  "productId": "id_sản_phẩm",
  "productName": "tên sản phẩm",
  "description": "Lý do ngắn gọn (1 câu Tiếng Anh) vì sao sản phẩm này hợp với da ${skinType}.",
  "hydrationBoost": 5, // Điểm cấp ẩm (1-10)
  "textureBoost": 4, // Điểm cải thiện bề mặt da (1-10)
  "price": 25.0
}`;

    const contents = [{ role: 'user', parts: [{ text: "Hãy tạo chu trình dưỡng da." }] }];
    
    // Config specifically for JSON output
    const config = {
      systemInstruction: systemInstruction,
      responseMimeType: "application/json",
      temperature: 0.1
    };

    const response = await callGeminiWithRetry(ai, 'gemini-2.5-flash', contents, config);
    let text = response.text;
    
    // Clean potential markdown just in case
    text = text.replace(/```json/g, '').replace(/```/g, '').trim();
    
    return JSON.parse(text);

  } catch (err) {
    console.error('[GEMINI] generateAiRoutine failed:', err.message);
    return generateFallbackRoutine(skinType, catalog, wantsSunscreen);
  }
}

function generateFallbackRoutine(skinType, catalog, wantsSunscreen) {
  // Simple heuristic fallback picking from catalog
  const routine = [];
  let stepIdx = 1;
  
  const categories = ['Cleanser', 'Toner', 'Serum', 'Cream'];
  if (wantsSunscreen) categories.push('Sunscreen');

  for (const cat of categories) {
    // Try to find a product in category matching skinType if possible, or just first one
    let prod = catalog.find(p => p.Category && p.Category.toLowerCase() === cat.toLowerCase());
    if (!prod) {
      // Fuzzy match
      prod = catalog.find(p => p.Category && p.Category.toLowerCase().includes(cat.toLowerCase()));
    }
    
    if (prod) {
      routine.push({
        step: stepIdx++,
        stepName: cat,
        productId: prod.Product_ID,
        productName: prod.Name,
        description: `Recommended for your ${skinType} skin to keep it healthy.`,
        hydrationBoost: Math.floor(Math.random() * 5) + 5,
        textureBoost: Math.floor(Math.random() * 5) + 4,
        price: prod.Price || 20.0
      });
    }
  }
  return routine;
}

module.exports = {
  processChatbotMessage,
  generateGeminiResponse,
  generateAiRoutine
};
