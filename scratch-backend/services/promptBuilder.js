/**
 * Prompt Builder Service for TirTir Gemini AI Chatbot
 * Builds dynamic system and user instructions incorporating profile and product context.
 */

function buildChatbotPrompt({ userProfile, productContext, productCatalogContext, message }) {
  const { skinType = 'combination', knownAllergies = [], loyaltyTier, preferredLanguage } = userProfile || {};
  
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

  // Append real product catalog if available (injected from recommendation intent)
  const catalogSection = productCatalogContext || '';

  const systemInstruction = `Bạn là một chuyên gia tư vấn chăm sóc da (skincare expert) Việt Nam của thương hiệu mỹ phẩm cao cấp TirTir.

${userContextStr}

${productContextStr}${catalogSection}

Hướng dẫn phản hồi BẮT BUỘC:
1. Trả lời hoàn toàn bằng tiếng Việt, văn phong thân thiện, chuyên nghiệp, tận tâm.
2. Trả lời THẲNG VÀO VẤN ĐỀ, tối đa 180 từ.
3. Khi khách hàng hỏi về một sản phẩm CỤ THỂ, BẮT BUỘC phải phân tích độ phù hợp dựa trên: Thành phần (ingredients) của sản phẩm đó có tác động thế nào đến Loại da (skin type) của khách. Chỉ rõ thành phần nào tốt, thành phần nào cần lưu ý cho loại da của họ.
4. Tránh trả lời chung chung kiểu "hãy uống nhiều nước", "ăn uống điều độ" nếu khách đang hỏi trực tiếp về sản phẩm. Chỉ đưa ra lời khuyên chung khi khách hỏi về cách chăm sóc da tổng thể.
5. Khi được hỏi gợi ý sản phẩm chung, PHẢI ngay lập tức gợi ý 2-3 sản phẩm CỤ THỂ từ danh mục TirTir (nếu có). KHÔNG được hỏi ngược lại user muốn biết sản phẩm nào.
6. Tuyệt đối không tự bịa đặt các thông tin thành phần sản phẩm ngoài những gì được cung cấp.
7. Nếu cuộc trò chuyện hoặc tin nhắn của khách hàng thể hiện rõ họ thuộc một loại da khác với loại da hiện tại, hãy đính kèm ở CUỐI CÙNG của phản hồi một chuỗi JSON chuẩn (dạng {"detectedSkinType": "loại_da"}), trong đó loại_da bắt buộc chỉ thuộc một trong các giá trị: "dry", "oily", "combination", "normal", "sensitive".`;

  return {
    systemInstruction,
    userMessage: message
  };
}

module.exports = {
  buildChatbotPrompt
};
