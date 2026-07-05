const { GoogleGenAI } = require('@google/genai');
const admin = require('firebase-admin');
const User = require('../models/User');
const Product = require('../models/Product');

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
  let productIngredients = '';
  let productName = '';
  if (productId) {
    try {
      const product = await Product.findOne({
        $or: [{ Product_ID: productId }, { _id: productId }]
      });
      if (product) {
        productName = product.Name;
        productIngredients = product.Key_Ingredients || product.Full_Description || '';
      }
    } catch (err) {
      console.error('[GEMINI] Mongo product read error:', err.message);
    }
  }

  // 3. Build dynamic systemInstruction
  const systemInstruction = `You are a premium skincare consultant for TirTir, a high-end Korean beauty brand.
Customer profile:
- Skin type: ${skinType}
- Known allergies/concerns: ${knownAllergies.join(', ') || 'None noted'}
${productId && productName ? `Product being discussed: "${productName}". Key ingredients: ${productIngredients || 'Not available'}.` : ''}

Guidelines:
- Reply in English, be warm, professional, and concise (max 120 words).
- Give helpful, science-backed skincare advice.
- If asked about a product, explain if it suits the customer's skin type.
- Recommend TirTir products where relevant.
- Never fabricate ingredient information beyond what is provided.`;

  const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
  let replyText = '';

  console.log(`[GEMINI] Processing message. userId=${userId}, apiKeySet=${!!(apiKey && !apiKey.includes('your_') && apiKey.trim() !== '')}`);

  if (!apiKey || apiKey.includes('your_') || apiKey.trim() === '') {
    console.warn('[GEMINI] API key not configured — using smart fallback response');
    replyText = generateSmartFallback(message.trim(), skinType, productName);
  } else {
    try {
      const ai = new GoogleGenAI({ apiKey });
      // contents must be an array of { role, parts } objects
      const contents = [
        {
          role: 'user',
          parts: [{ text: message.trim() }]
        }
      ];
      console.log(`[GEMINI] Calling Gemini API with model gemini-1.5-flash`);
      const response = await callGeminiWithRetry(
        ai,
        'gemini-1.5-flash',
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
 * Returns helpful, relevant content rather than a generic error.
 */
function generateSmartFallback(message, skinType, productName) {
  const msgLower = message.toLowerCase();

  if (productName) {
    return `${productName} is a great TirTir product! For ${skinType} skin, I'd recommend using it as directed and patch testing first. This product is formulated to suit various skin types. Would you like tips on how to incorporate it into your routine?`;
  }

  if (msgLower.includes('sunscreen') || msgLower.includes('spf') || msgLower.includes('uv')) {
    return `Sunscreen is essential daily skincare! For ${skinType} skin, look for SPF 30+ broad-spectrum protection. Apply as the last step of your morning routine, about 15 minutes before sun exposure. TirTir's Hydro UV Shield is great for lightweight, non-greasy protection. ☀️`;
  }

  if (msgLower.includes('routine') || msgLower.includes('steps')) {
    return `A great skincare routine for ${skinType} skin: 1️⃣ Gentle cleanser → 2️⃣ Hydrating toner → 3️⃣ Targeted serum → 4️⃣ Moisturizer → 5️⃣ SPF (AM only). TirTir has products for each step — shall I recommend specific ones for your skin type?`;
  }

  if (msgLower.includes('ingredient') || msgLower.includes('hyaluronic') || msgLower.includes('niacinamide') || msgLower.includes('retinol')) {
    return `Great question about skincare ingredients! For ${skinType} skin, hyaluronic acid adds lightweight hydration, niacinamide reduces pores and brightens, and retinol promotes cell turnover. Always introduce new actives gradually and use sunscreen when using retinol. Would you like more specific advice?`;
  }

  if (msgLower.includes('order') || msgLower.includes('shipping') || msgLower.includes('track')) {
    return `For order and shipping inquiries, please check your Order History in the app. If you need help, our customer service team can assist you. Your beauty journey is our priority! 💌`;
  }

  if (msgLower.includes('skin type') || msgLower.includes('my skin') || msgLower.includes('dry') || msgLower.includes('oily') || msgLower.includes('combination')) {
    return `Based on your profile, you have ${skinType} skin. Key tips: stay hydrated, use products suited for your skin type, and be consistent with your routine. TirTir has a curated range perfect for ${skinType} skin. Want personalized product recommendations?`;
  }

  return `Hi! I'm your TirTir Beauty Advisor. 🌸 I can help with skincare routines, product recommendations for your ${skinType} skin, ingredient advice, and more. What would you like to know?`;
}

/**
 * Simplified Gemini response for chatbotController
 */
async function generateGeminiResponse(systemInstruction, userMessage) {
  try {
    const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
    if (!apiKey || apiKey.includes('your_') || apiKey.trim() === '') {
      console.warn('[GEMINI] generateGeminiResponse: API key not configured');
      return generateSmartFallback(userMessage, 'combination', null);
    }
    const ai = new GoogleGenAI({ apiKey });
    const contents = [{ role: 'user', parts: [{ text: userMessage }] }];
    const response = await callGeminiWithRetry(ai, 'gemini-1.5-flash', contents, { systemInstruction });
    const text = response.text;
    if (text && text.trim()) return text.trim();
    return generateSmartFallback(userMessage, 'combination', null);
  } catch (err) {
    console.error('[GEMINI] generateGeminiResponse failed:', err.message);
    return generateSmartFallback(userMessage, 'combination', null);
  }
}

module.exports = {
  processChatbotMessage,
  generateGeminiResponse,
};
