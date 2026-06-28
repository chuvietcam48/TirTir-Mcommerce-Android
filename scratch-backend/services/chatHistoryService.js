const admin = require('firebase-admin');

const VALID_SKIN_TYPES = ['dry', 'oily', 'combination', 'normal', 'sensitive'];

/**
 * Extracts and validates detected skin type from Gemini raw response text
 */
function parseAndValidateDetectedSkinType(responseText) {
  if (!responseText) return { cleanReply: '', detectedSkinType: null };

  let cleanReply = responseText;
  let detectedSkinType = null;

  const jsonMatch = responseText.match(/\{\s*"detectedSkinType"\s*:\s*"([^"]+)"\s*\}/i);
  if (jsonMatch) {
    const rawDetected = jsonMatch[1].toLowerCase().trim();
    if (VALID_SKIN_TYPES.includes(rawDetected)) {
      detectedSkinType = rawDetected;
    }
    // Clean JSON block from user visible reply
    cleanReply = responseText.replace(/\{\s*"detectedSkinType"\s*:\s*"([^"]+)"\s*\}/gi, '').trim();
  }

  return { cleanReply, detectedSkinType };
}

/**
 * Persist conversation record to Firestore users/{uid}/chat_history
 */
async function saveChatHistory({ userId, userMessage, botMessage, productId = null, productName = null, skinType = 'Chưa xác định', detectedSkinType = null }) {
  if (!userId) return;

  try {
    const db = admin.firestore();
    const chatHistoryRef = db.collection('users').doc(String(userId)).collection('chat_history');

    const historyRecord = {
      userMessage,
      botMessage,
      productId: productId || null,
      productName: productName || null,
      skinType,
      detectedSkinType: detectedSkinType || null,
      suggestedSkinType: detectedSkinType || null,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    };

    await chatHistoryRef.add(historyRecord);
  } catch (err) {
    console.error('[CHAT_HISTORY] Error persisting chat history to Firestore:', err.message);
  }
}

module.exports = {
  parseAndValidateDetectedSkinType,
  saveChatHistory,
  VALID_SKIN_TYPES
};
