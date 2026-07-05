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

// POST /api/v1/ai/recommend-routine
exports.recommendRoutine = async (req, res) => {
  try {
    const { skinType, concerns } = req.body;
    // Assuming auth middleware is used, userId might be available.
    // Since the app didn't pass userId in body, let's just check if it's in req.user
    const userId = req.user ? req.user.id : null;
    
    let baseKeyword = "";
    
    if (userId) {
      // Find past valid orders for this user
      const pastOrders = await Order.find({ userId: userId, status: { $ne: 'Cancelled' } });
      if (pastOrders && pastOrders.length > 0) {
        // Extract keywords from their purchased products (e.g. Matcha, Milk, SOS)
        for (const order of pastOrders) {
           for (const item of order.items) {
              if (item.name) {
                 if (item.name.toLowerCase().includes('matcha')) baseKeyword = "Matcha";
                 else if (item.name.toLowerCase().includes('milk')) baseKeyword = "Milk";
                 else if (item.name.toLowerCase().includes('sos')) baseKeyword = "SOS";
              }
           }
        }
      }
    }
    
    // If we have a base keyword from order, try to build routine around it, otherwise use concerns/skinType
    const primaryConcern = (concerns && concerns.length > 0) ? concerns[0] : (skinType || "Hydration");
    const searchRegex = baseKeyword ? new RegExp(baseKeyword, 'i') : new RegExp(primaryConcern, 'i');
    
    // Helper to get a product for a step, excluding makeup
    const getProductForStep = async (stepName, categoryKeywords) => {
      let query = {
         Status: { $ne: 'inactive' },
         Stock_Quantity: { $gt: 0 },
         $and: [
            {
               $or: [
                  { Category: { $regex: categoryKeywords, $options: 'i' } },
                  { Name: { $regex: categoryKeywords, $options: 'i' } }
               ]
            },
            { Category: { $not: /makeup|cushion|foundation|concealer/i } },
            { Name: { $not: /cushion|foundation|concealer|setting spray/i } }
         ]
      };
      
      // Try finding one matching the user's baseKeyword or concern first
      let product = await Product.findOne({
          ...query,
          $or: [
              { Skin_Type_Target: { $regex: searchRegex } },
              { Name: { $regex: searchRegex } }
          ]
      }).select('Name Category Thumbnail_Images Product_ID Price Sale_Price');
      
      // Fallback if not found
      if (!product) {
         product = await Product.findOne(query).select('Name Category Thumbnail_Images Product_ID Price Sale_Price');
      }
      return product;
    };

    const steps = [
       { name: 'Cleanser', keywords: 'Cleanser|Wash' },
       { name: 'Toner', keywords: 'Toner' },
       { name: 'Serum', keywords: 'Serum|Ampoule' },
       { name: 'Cream', keywords: 'Cream|Moisturizer' },
       { name: 'Sunscreen', keywords: 'Sunscreen|SPF|UV Shield' }
    ];
    
    const routine = [];
    
    for (const step of steps) {
       const prod = await getProductForStep(step.name, step.keywords);
       if (prod) {
          // Extract the first thumbnail URL (Thumbnail_Images can be array or string)
          let thumbUrl = '';
          if (Array.isArray(prod.Thumbnail_Images) && prod.Thumbnail_Images.length > 0) {
            thumbUrl = prod.Thumbnail_Images[0];
          } else if (typeof prod.Thumbnail_Images === 'string' && prod.Thumbnail_Images) {
            // Could be JSON stringified array
            try {
              const parsed = JSON.parse(prod.Thumbnail_Images);
              thumbUrl = Array.isArray(parsed) ? (parsed[0] || '') : prod.Thumbnail_Images;
            } catch {
              thumbUrl = prod.Thumbnail_Images;
            }
          }
          routine.push({
             step: step.name,
             product: {
               ...prod.toObject ? prod.toObject() : prod,
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
