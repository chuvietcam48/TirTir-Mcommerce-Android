const { GoogleGenerativeAI } = require('@google/generative-ai');
const User = require('../models/user.model');
const Product = require('../models/product.model');
const { getFirestore, isFirebaseEnabled } = require('../services/firebaseAdmin.service');

exports.getChatbotMessage = async (req, res) => {
    try {
        const { message, productId } = req.body;
        if (!message || !message.trim()) {
            return res.status(400).json({ success: false, message: 'Message is required' });
        }

        const userId = req.user?.id;
        let skinType = 'Chưa xác định';
        let knownAllergies = [];
        let firebaseUid = null;

        // 1. Fetch User Profile
        if (userId) {
            const user = await User.findById(userId);
            if (user) {
                firebaseUid = user.firebaseUid;
                skinType = user.skinProfile?.skinType || 'Chưa xác định';
                knownAllergies = user.skinProfile?.concerns || [];
            }
        }

        // Try syncing from Firestore if firebase is active
        if (firebaseUid && isFirebaseEnabled()) {
            try {
                const db = getFirestore();
                if (db) {
                    const userDoc = await db.collection('users').doc(firebaseUid).get();
                    if (userDoc.exists) {
                        const userData = userDoc.data();
                        if (userData.skinType) skinType = userData.skinType;
                        if (userData.knownAllergies) knownAllergies = userData.knownAllergies;
                    }
                }
            } catch (err) {
                console.error('[BE2][CHATBOT] Firestore profile fetch error:', err.message);
            }
        }

        // 2. Fetch Product Context
        let productInfoStr = '';
        if (productId) {
            try {
                const product = await Product.findById(productId);
                if (product) {
                    productInfoStr = `Tên: ${product.Name}, Thành phần: ${Array.isArray(product.Ingredients) ? product.Ingredients.join(', ') : product.Ingredients || 'Không rõ'}.`;
                }
            } catch (err) {
                console.error('[BE2][CHATBOT] Product context fetch error:', err.message);
            }
        }

        // 3. Fetch products to recommend
        let productsListText = '';
        try {
            const products = await Product.find({ Stock_Quantity: { $gt: 0 } }).limit(10).select('Name Category Price _id');
            productsListText = products.map(p => `- ID: ${p._id}, Tên: ${p.Name}, Category: ${p.Category}`).join('\n');
        } catch (err) {
            console.error('[BE2][CHATBOT] Products list fetch error:', err.message);
        }

        // 4. Initialize Gemini
        const apiKey = process.env.GEMINI_API_KEY;
        let useGemini = false;
        let model = null;

        if (apiKey && apiKey !== 'your_gemini_api_key_here' && !apiKey.includes('your_')) {
            try {
                // FORCE API version v1 to avoid v1beta 404 errors
                const genAI = new GoogleGenerativeAI(apiKey);
                model = genAI.getGenerativeModel({
                    model: 'gemini-1.5-flash'
                });
                useGemini = true;
            } catch (err) {
                console.error('[BE2][CHATBOT] Gemini init failed:', err.message);
            }
        }

        const systemPrompt = `Bạn là một chuyên gia chăm sóc da (skincare) Việt Nam của thương hiệu mỹ phẩm TirTir.
Hãy trả lời câu hỏi của khách hàng bằng tiếng Việt một cách khoa học, tận tâm và chuyên nghiệp.

Thông tin khách hàng:
- Loại da hiện tại: ${skinType}
- Dị ứng hoặc vấn đề da quan tâm: ${knownAllergies.join(', ') || 'Không có'}

${productInfoStr ? `Khách hàng đang xem sản phẩm: ${productInfoStr}. Hãy tập trung tư vấn về sản phẩm này, kiểm tra xem thành phần của nó có phù hợp với loại da và dị ứng của khách hay không.` : ''}

Bạn phải phản hồi ở định dạng JSON với các khóa chính xác sau:
{
  "reply": "Nội dung câu trả lời tư vấn cho khách hàng bằng tiếng Việt",
  "detectedSkinType": "Loại da bạn nhận diện được từ tin nhắn của khách (Oily/Dry/Sensitive/Combination/Normal), giữ nguyên loại da cũ nếu khách không đề cập loại da mới",
  "recommendedProductIds": ["Mảng các ID sản phẩm phù hợp được đề xuất từ danh sách sản phẩm bên dưới nếu có"]
}

Danh sách sản phẩm gợi ý sẵn (nếu phù hợp, hãy đưa ID của chúng vào "recommendedProductIds"):
${productsListText}
`;

        let chatbotReply;

        if (useGemini && model) {
            const timeoutPromise = new Promise((_, reject) => {
                setTimeout(() => reject(new Error('Timeout')), 10000);
            });

            let rawResponseText = "";
            const callGeminiWithRetry = async (retries = 1, timeoutMs = 5000) => {
                for (let attempt = 0; attempt <= retries; attempt++) {
                    try {
                        const timeoutPromise = new Promise((_, reject) => {
                            setTimeout(() => reject(new Error('Timeout')), timeoutMs);
                        });

                        const prompt = `Tin nhắn của khách hàng: "${message}"\nTUYỆT ĐỐI KHÔNG sử dụng thẻ markdown \`\`\`json. Chỉ trả về chuỗi JSON thuần tuý hợp lệ.`;
                        const geminiCall = (async () => {
                            const result = await model.generateContent([
                                { text: systemPrompt },
                                { text: prompt }
                            ]);
                            return result.response.text();
                        })();

                        rawResponseText = await Promise.race([geminiCall, timeoutPromise]);
                        return rawResponseText;
                    } catch (err) {
                        console.error(`[BE2][CHATBOT] Gemini attempt ${attempt + 1} failed:`, err.message);
                        if (attempt === retries || err.status === 400 || err.status === 401 || err.status === 403) {
                            throw err; // Stop retrying for permanent errors or out of retries
                        }
                        console.warn(`[BE2][CHATBOT] Retrying Gemini call...`);
                    }
                }
            };

            try {
                rawResponseText = await callGeminiWithRetry(1, 5000);
                
                // Strip markdown wrappers safely
                let cleanJsonString = rawResponseText.trim();
                if (cleanJsonString.startsWith('```json')) {
                    cleanJsonString = cleanJsonString.replace(/^```json\s*/, '').replace(/\s*```$/, '');
                } else if (cleanJsonString.startsWith('```')) {
                    cleanJsonString = cleanJsonString.replace(/^```\s*/, '').replace(/\s*```$/, '');
                }

                chatbotReply = JSON.parse(cleanJsonString.trim());
            } catch (err) {
                console.error('[BE2][CHATBOT] Failed to get/parse Gemini response.', err);
                console.error('[BE2][CHATBOT] Raw Response before crash:', rawResponseText);
                
                if (err.message === 'Timeout') {
                    chatbotReply = {
                        reply: "Xin lỗi, hệ thống AI đang quá tải. Bạn có thể hỏi lại hoặc sử dụng các sản phẩm khuyên dùng.",
                        detectedSkinType: skinType,
                        recommendedProductIds: []
                    };
                } else {
                    chatbotReply = {
                        reply: "Hiện tại tôi đang gặp khó khăn khi kết nối với máy chủ AI. Xin bạn vui lòng thử lại sau.",
                        detectedSkinType: skinType,
                        recommendedProductIds: []
                    };
                }
            }
        } else {
            // Heuristic fallback if Gemini API Key is not set
            chatbotReply = {
                reply: `Chào bạn, tôi là trợ lý TirTir. Da của bạn là da ${skinType}. Bạn nên sử dụng các sản phẩm nhẹ dịu và tránh các thành phần gây dị ứng.`,
                detectedSkinType: skinType,
                recommendedProductIds: []
            };
        }

        // 5. Persist Chat to Firestore
        if (firebaseUid && isFirebaseEnabled()) {
            try {
                const db = getFirestore();
                if (db) {
                    const chatHistoryRef = db.collection('users').doc(firebaseUid).collection('chat_history');
                    await chatHistoryRef.add({
                        sender: 'user',
                        text: message,
                        createdAt: new Date().toISOString()
                    });
                    await chatHistoryRef.add({
                        sender: 'bot',
                        text: chatbotReply.reply,
                        detectedSkinType: chatbotReply.detectedSkinType,
                        recommendedProductIds: chatbotReply.recommendedProductIds,
                        createdAt: new Date().toISOString()
                    });
                    console.log(`[BE2][CHATBOT] Persisted chat messages to Firestore users/${firebaseUid}/chat_history`);
                }
            } catch (fsErr) {
                console.error('[BE2][CHATBOT] Firestore chat history persistence error:', fsErr.message);
            }
        }

        // Also persist to MongoDB ChatHistory if user is logged in
        if (userId) {
            try {
                const ChatHistory = require('../models/chat.history.model');
                await ChatHistory.findOneAndUpdate(
                    { user: userId },
                    {
                        $push: {
                            messages: {
                                $each: [
                                    { text: message, sender: 'user', timestamp: new Date() },
                                    { text: chatbotReply.reply, sender: 'bot', timestamp: new Date() }
                                ],
                                $slice: -200
                            }
                        }
                    },
                    { upsert: true, new: true }
                );
            } catch (dbErr) {
                console.error('[BE2][CHATBOT] MongoDB chat history persistence error:', dbErr.message);
            }
        }

        // Return response
        return res.status(200).json({
            success: true,
            reply: chatbotReply.reply,
            detectedSkinType: chatbotReply.detectedSkinType,
            recommendedProductIds: chatbotReply.recommendedProductIds
        });

    } catch (error) {
        console.error('[BE2][CHATBOT] Chatbot Error:', error);
        return res.status(500).json({
            success: false,
            message: 'Server error during chatbot message processing'
        });
    }
};
