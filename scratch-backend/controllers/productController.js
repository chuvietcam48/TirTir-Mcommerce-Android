const Product = require('../models/Product');

// GET /api/v1/products
// Query params:
//   limit    – max number of results (default 100, max 1000)
//   category – filter by Category or Category_Slug (case-insensitive substring)
exports.getProducts = async (req, res) => {
  const rawLimit = parseInt(req.query.limit, 10);
  const limit = isNaN(rawLimit) || rawLimit < 1 ? 100 : Math.min(rawLimit, 1000);
  const rawPage = parseInt(req.query.page, 10);
  const page = isNaN(rawPage) || rawPage < 1 ? 1 : rawPage;
  
  const { category, skin_type } = req.query;
  const filter = {};
  if (category) {
    filter.$or = [
      { Category: { $regex: category, $options: 'i' } },
      { Category_Slug: { $regex: category, $options: 'i' } },
    ];
  }
  if (skin_type) {
    filter.Skin_Type_Target = { $regex: skin_type, $options: 'i' };
  }

  const skip = (page - 1) * limit;
  let sortQuery = { Created_At: -1 }; // default newest
  if (req.query.sort) {
    if (req.query.sort === 'price_asc') sortQuery = { Price: 1 };
    else if (req.query.sort === 'price_desc') sortQuery = { Price: -1 };
    else if (req.query.sort === 'newest') sortQuery = { Created_At: -1 };
  }

  let products = await Product.find(filter).sort(sortQuery).skip(skip).limit(limit).lean();
  
  // Fix CDN URLs
  const cdnBase = process.env.CDN_BASE_URL || 'https://tirtir.vn/wp-content/uploads/2024/';
  products = products.map(p => {
    if (p.Thumbnail_Images && !p.Thumbnail_Images.startsWith('http')) {
      p.Thumbnail_Images = cdnBase + p.Thumbnail_Images;
    }
    if (p.Description_Images && Array.isArray(p.Description_Images)) {
      p.Description_Images = p.Description_Images.map(img => img.startsWith('http') ? img : cdnBase + img);
    }
    if (p.Gallery_Images && Array.isArray(p.Gallery_Images)) {
      p.Gallery_Images = p.Gallery_Images.map(img => img.startsWith('http') ? img : cdnBase + img);
    }
    return p;
  });
  const total = await Product.countDocuments(filter);

  const categoriesAgg = await Product.aggregate([
    { $group: { _id: "$Category", count: { $sum: 1 } } },
    { $match: { _id: { $ne: null } } },
    { $project: { _id: 0, name: "$_id", count: 1 } }
  ]);

  res.status(200).json({ 
    success: true, 
    total: total,
    page: page,
    limit: limit,
    data: products, 
    categories: categoriesAgg
  });
};

// GET /api/v1/products/:id
exports.getProductById = async (req, res) => {
  const product = await Product.findById(req.params.id).lean();
  if (!product) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy sản phẩm.' });
  }

  const cdnBase = process.env.CDN_BASE_URL || 'https://tirtir.vn/wp-content/uploads/2024/';
  if (product.Thumbnail_Images && !product.Thumbnail_Images.startsWith('http')) {
    product.Thumbnail_Images = cdnBase + product.Thumbnail_Images;
  }
  if (product.Description_Images && Array.isArray(product.Description_Images)) {
    product.Description_Images = product.Description_Images.map(img => img.startsWith('http') ? img : cdnBase + img);
  }
  if (product.Gallery_Images && Array.isArray(product.Gallery_Images)) {
    product.Gallery_Images = product.Gallery_Images.map(img => img.startsWith('http') ? img : cdnBase + img);
  }

  res.status(200).json({ success: true, data: product });
};

// GET /api/v1/products/cushion-match?skin_tone_hex=#D8A087
exports.matchCushion = async (req, res) => {
  const { skin_tone_hex } = req.query;
  if (!skin_tone_hex) {
    return res.status(400).json({ success: false, message: 'Missing skin_tone_hex' });
  }

  // Parse target hex
  const target = hexToRgb(skin_tone_hex);

  // Import Shade model dynamically in case it is defined in the backend/models directory
  let Shade;
  try {
    Shade = require('../backend/models/shade.model');
  } catch (err) {
    return res.status(500).json({ success: false, message: 'Shade model not found' });
  }

  // Fetch all shades from MongoDB
  const allShades = await Shade.find({ Hex_Code: { $exists: true, $ne: null } }).lean();

  if (!allShades || allShades.length === 0) {
    return res.status(404).json({ success: false, message: 'No shades found in database' });
  }

  // Calculate Euclidean distance and map to match score (100 = exact match)
  const results = allShades.map(shade => {
    let hex = shade.Hex_Code;
    if (!hex.startsWith('#')) hex = '#' + hex;
    const c = hexToRgb(hex);
    
    // Fallback if r, g, b are predefined in db
    if (shade.R !== undefined && shade.G !== undefined && shade.B !== undefined) {
      c.r = shade.R;
      c.g = shade.G;
      c.b = shade.B;
    }
    
    const distance = Math.sqrt(Math.pow(target.r - c.r, 2) + Math.pow(target.g - c.g, 2) + Math.pow(target.b - c.b, 2));
    
    // Max distance in RGB space is ~441.6. Use an exponential or linear scale.
    // The frontend expects matchScore to be a percentage or a delta. 
    // Wait, the frontend code says: matchPercent = 100 * exp(-matchScore / 7)
    // So if we return matchScore = delta-E distance (0 to 100).
    const matchScore = parseFloat(distance.toFixed(1));
    const matchPercent = Math.round(100 * Math.exp(-matchScore / 7.0));

    return {
      productId: shade.Product_ID || `cushion-${shade.Shade_Code ? shade.Shade_Code.toLowerCase() : ''}`,
      productName: shade.Shade_Category_Name || `Mask Fit Red Cushion`,
      shadeName: shade.Shade_Name,
      shadeHex: hex,
      matchScore: matchScore,
      matchPercent: matchPercent,
      imageUrl: shade.Shade_Image || "https://tirtir.vn/wp-content/uploads/2024/05/Mask-Fit-Red-Cushion.jpg"
    };
  });

  // Sort by lowest matchScore (lowest distance = best match)
  results.sort((a, b) => a.matchScore - b.matchScore);

  res.status(200).json({
    success: true,
    data: results
  });
};

function hexToRgb(hex) {
  let result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : { r: 255, g: 255, b: 255 };
}

// POST /api/v1/products
exports.createProduct = async (req, res) => {
  try {
    const product = await Product.create(req.body);
    res.status(201).json({ success: true, data: product });
  } catch (error) {
    res.status(400).json({ success: false, message: error.message });
  }
};

// PUT /api/v1/products/:id
exports.updateProduct = async (req, res) => {
  try {
    const product = await Product.findByIdAndUpdate(req.params.id, req.body, {
      new: true,
      runValidators: true
    });
    if (!product) return res.status(404).json({ success: false, message: 'Product not found' });
    res.status(200).json({ success: true, data: product });
  } catch (error) {
    res.status(400).json({ success: false, message: error.message });
  }
};

// DELETE /api/v1/products/:id
exports.deleteProduct = async (req, res) => {
  try {
    const product = await Product.findByIdAndDelete(req.params.id);
    if (!product) return res.status(404).json({ success: false, message: 'Product not found' });
    res.status(200).json({ success: true, message: 'Product deleted' });
  } catch (error) {
    res.status(400).json({ success: false, message: error.message });
  }
};

