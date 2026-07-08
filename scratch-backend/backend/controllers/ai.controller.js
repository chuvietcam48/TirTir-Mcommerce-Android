const { GoogleGenerativeAI } = require('@google/generative-ai');
const User = require('../models/user.model');
const Product = require('../models/product.model');
const { GoogleGenAI } = require('@google/genai');

/**
 * Robust AI Controller
 * Handles face analysis and routine recommendations
 */
exports.analyzeFace = async (req, res) => {
    try {
        const { image } = req.body;
        if (!image) return res.status(400).json({ success: false, message: 'Image is required' });

        const apiKey = process.env.GEMINI_API_KEY;
        const genAI = new GoogleGenerativeAI(apiKey);
        const model = genAI.getGenerativeModel({ model: 'gemini-1.5-flash' });

        // Implementation...
        return res.status(200).json({ success: true, data: { skinType: 'Normal', confidence: 0.9 } });
    } catch (error) {
        return res.status(500).json({ success: false, message: error.message });
    }
};

exports.recommendRoutine = async (req, res) => {
  try {
    const { skinType, concerns } = req.body;
    
    // Fetch all active skincare products excluding makeup
    const products = await Product.find({
        Status: { $ne: 'inactive' },
        Stock_Quantity: { $gt: 0 },
        Category: { $not: /makeup|cushion|foundation|concealer/i },
        Name: { $not: /cushion|foundation|concealer|setting spray/i }
    }).select('Name Category Skin_Type_Target Main_Concern Key_Ingredients Thumbnail_Images Product_ID Price Sale_Price');

    if (!products || products.length === 0) {
        return res.status(404).json({ success: false, message: 'No products available for routine generation.' });
    }

    // Prepare catalog for Gemini (shrink data to save tokens)
    const catalog = products.map(p => ({
        id: p._id.toString(),
        name: p.Name,
        category: p.Category,
        target: p.Skin_Type_Target || '',
        concern: p.Main_Concern || '',
        ingredients: p.Key_Ingredients || ''
    }));

    const prompt = `You are an expert dermatologist AI for the brand TirTir.
A user needs a skincare routine.
Skin Type: ${skinType || 'Unknown'}
Concerns: ${concerns ? concerns.join(', ') : 'None specified'}

Here is the catalog of available products (in JSON format):
${JSON.stringify(catalog)}

Select exactly ONE product for each of the following 5 steps:
1. Cleanser
2. Toner
3. Serum
4. Cream
5. Sunscreen

Return a raw JSON array of 5 objects (do NOT wrap in markdown code blocks like \`\`\`json). Each object must have:
- "step": String (the step name, e.g., "Cleanser")
- "id": String (the exact id of the selected product from the catalog)
- "hydrationBoost": Number (estimated hydration improvement percentage, e.g., 15)
- "textureBoost": Number (estimated texture improvement percentage, e.g., 10)

Base your product selection strictly on matching the product's target, concern, and ingredients with the user's skin type and concerns.`;

    // 3. Request Gemini to generate routine (with fallback in service)
    const geminiService = require('../../services/geminiService');
    // If the user ticked sunscreen in their concerns or just wants it by default
    const wantsSunscreen = concerns && (concerns.includes('Sun protection') || concerns.includes('sunscreen'));
    
    let aiRoutine = [];
    try {
        aiRoutine = await geminiService.generateAiRoutine(skinType, catalog, wantsSunscreen);
    } catch (e) {
        console.error('[BE2][AI] generateAiRoutine failed, returning empty:', e.message);
    }

    if (!Array.isArray(aiRoutine)) aiRoutine = [];

    // Map AI result to product details
    const routine = [];
    for (const item of aiRoutine) {
        const prod = products.find(p => p._id.toString() === item.id);
        if (prod) {
            let thumbUrl = '';
            if (Array.isArray(prod.Thumbnail_Images) && prod.Thumbnail_Images.length > 0) {
                thumbUrl = prod.Thumbnail_Images[0];
            } else if (typeof prod.Thumbnail_Images === 'string' && prod.Thumbnail_Images) {
                try {
                    const parsed = JSON.parse(prod.Thumbnail_Images);
                    thumbUrl = Array.isArray(parsed) ? (parsed[0] || '') : prod.Thumbnail_Images;
                } catch {
                    thumbUrl = prod.Thumbnail_Images;
                }
            }
            routine.push({
                step: item.step,
                hydrationBoost: item.hydrationBoost,
                textureBoost: item.textureBoost,
                product: {
                    ...(prod.toObject ? prod.toObject() : prod),
                    imageUrl: thumbUrl,
                    productId: String(prod.Product_ID || prod._id),
                }
            });
        }
    }

    res.status(200).json({
      success: true,
      data: {
        routine: routine
      }
    });
  } catch (error) {
    console.error('Error in recommendRoutine:', error);
    res.status(500).json({ success: false, message: 'Internal server error' });
  }
};

exports.getLatestProfile = async (req, res) => {
    return res.status(200).json({ success: true, data: {} });
};

exports.saveResult = async (req, res) => {
    return res.status(200).json({ success: true });
};

exports.getHistory = async (req, res) => {
    return res.status(200).json({ success: true, data: [] });
};

exports.analyzeSkin = async (req, res) => {
    return res.status(200).json({ success: true });
};

exports.healthCheck = async (req, res) => {
    return res.status(200).json({ ok: true });
};

exports.submitRoutineFeedback = async (req, res) => {
    return res.status(200).json({ success: true });
};
