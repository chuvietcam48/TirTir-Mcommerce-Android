/**
 * Prompt Builder Service for TirTir Gemini AI Chatbot
 * Builds dynamic system and user instructions incorporating profile and product context.
 */

function buildChatbotPrompt({ userProfile, productContext, message }) {
  const { skinType = 'Chưa xác định', knownAllergies = [], loyaltyTier, preferredLanguage } = userProfile || {};
  
  let userContextStr = `Hồ sơ người dùng:\n- Loại da: ${skinType}\n- Dị ứng / Vấn đề da: ${knownAllergies.length > 0 ? knownAllergies.join(', ') : 'Không có'}`;
  if (loyaltyTier) userContextStr += `\n- Hạng thành viên: ${loyaltyTier}`;
  if (preferredLanguage) userContextStr += `\n- Ngôn ngữ ưu tiên: ${preferredLanguage}`;

  let productContextStr = 'Không có sản phẩm cụ thể được chọn.';
  if (productContext) {
    const { productName, brand, ingredients, category, skinTypeTarget, warnings } = productContext;
    productContextStr = `Sản phẩm đang tham chiếu:\n- Tên sản phẩm: ${productName || 'Không rõ'}`;
    if (brand) productContextStr += `\n- Thương hiệu: ${brand}`;
    if (category) productContextStr += `\n- Danh mục: ${category}`;
    if (skinTypeTarget) productContextStr += `\n- Phù hợp loại da: ${skinTypeTarget}`;
    if (ingredients) productContextStr += `\n- Thành phần: ${Array.isArray(ingredients) ? ingredients.join(', ') : ingredients}`;
    if (warnings) productContextStr += `\n- Lưu ý / Cảnh báo: ${warnings}`;
  }

  const systemInstruction = `Bạn là một chuyên gia tư vấn chăm sóc da (skincare expert) Việt Nam của thương hiệu mỹ phẩm cao cấp TirTir.

${userContextStr}

${productContextStr}

Hướng dẫn phản hồi:
1. Trả lời hoàn toàn bằng tiếng Việt, văn phong thân thiện, chuyên nghiệp, tận tâm.
2. Đưa ra câu trả lời ngắn gọn, tối đa 150 từ.
3. Tuyệt đối không tự bịa đặt các thông tin thành phần sản phẩm ngoài những gì được cung cấp.
4. Gợi ý chu trình chăm sóc da an toàn, bảo vệ da. Tránh đưa ra các chẩn đoán y khoa chuyên sâu.
5. Nếu cuộc trò chuyện hoặc tin nhắn của khách hàng thể hiện rõ họ thuộc một loại da khác với loại da hiện tại, hãy đính kèm ở CUỐI CÙNG của phản hồi một chuỗi JSON chuẩn (dạng {"detectedSkinType": "loại_da"}), trong đó loại_da bắt buộc chỉ thuộc một trong các giá trị: "dry", "oily", "combination", "normal", "sensitive".`;

  return {
    systemInstruction,
    userMessage: message
  };
}

module.exports = {
  buildChatbotPrompt
};
