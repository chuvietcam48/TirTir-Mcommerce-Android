const express = require('express');

// POST /api/v1/ai/analyze-ingredients
exports.analyzeIngredients = async (req, res) => {
  // Mocking the OCR result
  res.status(200).json({
    success: true,
    data: {
      ingredients: ['Water', 'Glycerin', 'Niacinamide', 'Hyaluronic Acid'],
      harmful: false,
      goodFor: ['Acne', 'Brightening', 'Hydration'],
      badFor: [],
      summary: 'Thành phần an toàn, cấp ẩm tốt và dưỡng sáng da hiệu quả.'
    }
  });
};

// POST /api/v1/ai/analyze-face
exports.analyzeFace = async (req, res) => {
  // Mocking the extended skin analysis response
  res.status(200).json({
    success: true,
    data: {
      skinTone: 'Fair',
      undertone: 'Warm',
      skinHex: '#f1d5c2',
      ITA_category: 'Light',
      texture: 'Smooth',
      pores: 'Small',
      hydration: 'High',
      recommendations: [
        'Sử dụng cushion tone sáng',
        'Tăng cường dưỡng ẩm nhẹ nhàng'
      ]
    }
  });
};

const Product = require('../models/Product');
const Order = require('../models/Order');

const { GoogleGenAI } = require('@google/genai');

// POST /api/v1/ai/recommend-routine
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

You MUST tailor the recommendations SPECIFICALLY to the user's Skin Type and Concerns!
Do NOT just pick the most popular products. Choose the absolute BEST match for their specific skin type.
For example:
- If Skin Type is Oily/Acne: pick lightweight products, BHA, Niacinamide, oil-free cream.
- If Skin Type is Dry: pick hydrating, ceramide, rich cream, hyaluronic acid.
- If Skin Type is Sensitive: pick soothing, centella, mild products.

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
- "textureBoost": Number (estimated texture improvement percentage, e.g., 10)`;

    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
    const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: prompt,
        config: {
            temperature: 0.7,
            topK: 40,
        }
    });

    let jsonString = response.text;
    if (jsonString.startsWith('```json')) {
        jsonString = jsonString.replace(/```json\n?/, '').replace(/\n?```$/, '');
    } else if (jsonString.startsWith('```')) {
        jsonString = jsonString.replace(/```\n?/, '').replace(/\n?```$/, '');
    }
    const aiRoutine = JSON.parse(jsonString.trim());

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
