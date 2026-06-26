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
