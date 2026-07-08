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

const { generateAiRoutine } = require('../services/geminiService');

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

    const wantsSunscreen = concerns && concerns.includes('sun_protection');

    // Call geminiService which handles retries and fallbacks
    const aiRoutine = await generateAiRoutine(skinType || 'Normal', products, concerns || []);

    // Map AI result to product details
    const routine = [];
    for (const item of aiRoutine) {
        const prod = products.find(p => String(p.Product_ID) === String(item.productId) || String(p._id) === String(item.productId));
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
            
            const productData = prod.toObject ? prod.toObject() : { ...prod };
            
            // Explicitly set BOTH possible ID fields for Android's SerializedName
            productData._id = String(prod._id);
            productData.id = String(prod._id); // Just in case Android Gson expects 'id'
            productData.Product_ID = String(prod.Product_ID || prod._id);
            productData.productId = String(prod.Product_ID || prod._id);
            
            // Fix CDN URLs directly here so Android doesn't have to guess
            const cdnBase = process.env.CDN_BASE_URL || 'https://tirtir-project.onrender.com/';
            if (thumbUrl && !thumbUrl.startsWith('http')) {
                // Remove leading slash if present
                let cleanPath = thumbUrl.startsWith('/') ? thumbUrl.substring(1) : thumbUrl;
                
                // Automatically fix missing subfolders for products
                if (cleanPath.startsWith('assets/images/products/')) {
                    const afterPrefix = cleanPath.substring('assets/images/products/'.length);
                    const segments = afterPrefix.split('/');
                    if (segments.length === 2) {
                        cleanPath = `assets/images/products/${segments[0]}/Main-Images/${segments[1]}`;
                    }
                }
                thumbUrl = cdnBase + cleanPath;
            }
            productData.Thumbnail_Images = thumbUrl; // Replace raw JSON array with clean ABSOLUTE url

            routine.push({
                step: item.step,
                stepName: item.stepName,
                description: item.description,
                hydrationBoost: item.hydrationBoost || 5,
                textureBoost: item.textureBoost || 5,
                product: productData
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
