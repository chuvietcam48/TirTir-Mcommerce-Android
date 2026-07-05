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
  const cdnBase = process.env.CDN_BASE_URL || 'https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product';
  products = products.map(p => {
    if (p.Thumbnail_Images && !p.Thumbnail_Images.startsWith('http')) {
      p.Thumbnail_Images = cdnBase;
    }
    if (p.Description_Images && Array.isArray(p.Description_Images)) {
      p.Description_Images = p.Description_Images.map(img => img.startsWith('http') ? img : cdnBase);
    }
    if (p.Gallery_Images && Array.isArray(p.Gallery_Images)) {
      p.Gallery_Images = p.Gallery_Images.map(img => img.startsWith('http') ? img : cdnBase);
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

  const cdnBase = process.env.CDN_BASE_URL || 'https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product';
  if (product.Thumbnail_Images && !product.Thumbnail_Images.startsWith('http')) {
    product.Thumbnail_Images = cdnBase;
  }
  if (product.Description_Images && Array.isArray(product.Description_Images)) {
    product.Description_Images = product.Description_Images.map(img => img.startsWith('http') ? img : cdnBase);
  }
  if (product.Gallery_Images && Array.isArray(product.Gallery_Images)) {
    product.Gallery_Images = product.Gallery_Images.map(img => img.startsWith('http') ? img : cdnBase);
  }

  res.status(200).json({ success: true, data: product });
};

// GET /api/v1/products/cushion-match?skin_tone_hex=#D8A087
exports.matchCushion = async (req, res) => {
  const { skin_tone_hex } = req.query;
  if (!skin_tone_hex) {
    return res.status(400).json({ success: false, message: 'Missing skin_tone_hex' });
  }

  const userRgb = hexToRgb(skin_tone_hex);
  if (!userRgb) {
    return res.status(400).json({ success: false, message: 'Invalid hex code' });
  }
  const userLab = rgbToLab(userRgb);

  // Full TirTir Cushion Shade Dataset (hardcoded fallback khi DB không có shade_color_hex)
  const shadeDataset = [
    { code: "17C", name: "17C Porcelain", hex: "#f9d9c2", productName: "Mask Fit Red Cushion", price: 35.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "21N", name: "21N Ivory", hex: "#ebc5a1", productName: "Mask Fit Red Cushion", price: 35.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "23N", name: "23N Sand", hex: "#ebbf98", productName: "Mask Fit Red Cushion", price: 35.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "24N", name: "24N Latte", hex: "#e4b58e", productName: "Mask Fit Aura Cushion", price: 38.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "25N", name: "25N Mocha", hex: "#d9ab82", productName: "Mask Fit All-Cover Cushion", price: 36.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "27N", name: "27N Camel", hex: "#e5b98b", productName: "Mask Fit Red Cushion", price: 35.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "30N", name: "30N Honey", hex: "#d8a178", productName: "Mask Fit Aura Cushion", price: 38.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "33N", name: "33N Macchiato", hex: "#d3a177", productName: "Mask Fit All-Cover Cushion", price: 36.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "37N", name: "37N Walnut", hex: "#b88a5e", productName: "Mask Fit All-Cover Cushion", price: 36.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" },
    { code: "43N", name: "43N Deep Cocoa", hex: "#a36a42", productName: "Mask Fit Red Cushion", price: 35.00, imageUrl: "https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product" }
  ];

  // Try fetching real products from MongoDB first
  let results = [];
  try {
    const products = await Product.find({
      Category: { $regex: /cushion|foundation/i },
      shade_color_hex: { $ne: null }
    }).select('Product_ID Name Price Sale_Price Thumbnail_Images Category shade_color_hex').lean();

    if (products && products.length > 0) {
      const cdnBase = process.env.CDN_BASE_URL || 'https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Product';
      results = products.map(p => {
        const prodRgb = hexToRgb(p.shade_color_hex);
        let deltaE = 999;
        if (prodRgb) {
          const prodLab = rgbToLab(prodRgb);
          deltaE = calculateDeltaE(userLab, prodLab);
        }
        let imgUrl = p.Thumbnail_Images || '';
        if (imgUrl && !imgUrl.startsWith('http')) imgUrl = cdnBase;

        return {
          Product_ID: p.Product_ID || p._id.toString(),
          Shade_Name: p.Name || '',
          matchScore: parseFloat(deltaE.toFixed(2)),
          productName: p.Name || 'TirTir Cushion',
          imageUrl: imgUrl,
          price: p.Price || 0,
          salePrice: p.Sale_Price || 0,
          shadeHex: p.shade_color_hex
        };
      });
    }
  } catch (dbErr) {
    console.warn('DB query failed for cushion-match, using fallback dataset:', dbErr.message);
  }

  // Fallback: use hardcoded dataset if DB returned nothing
  if (results.length === 0) {
    results = shadeDataset.map(shade => {
      const shadeRgb = hexToRgb(shade.hex);
      const shadeLab = rgbToLab(shadeRgb);
      const deltaE = calculateDeltaE(userLab, shadeLab);

      return {
        Product_ID: `cushion-${shade.code.toLowerCase()}`,
        Shade_Name: shade.name,
        matchScore: parseFloat(deltaE.toFixed(2)),
        productName: shade.productName,
        imageUrl: shade.imageUrl,
        price: shade.price,
        salePrice: 0,
        shadeHex: shade.hex
      };
    });
  }

  // Sort by matchScore ascending (lowest deltaE = best match)
  results.sort((a, b) => a.matchScore - b.matchScore);

  res.status(200).json({
    success: true,
    data: results
  });
};

// ---- Color Science Helpers ----

function hexToRgb(hex) {
  if (!hex) return null;
  const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
  hex = hex.replace(shorthandRegex, (m, r, g, b) => r + r + g + g + b + b);
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : null;
}

function rgbToLab(rgb) {
  let r = rgb.r / 255, g = rgb.g / 255, b = rgb.b / 255;
  r = (r > 0.04045) ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
  g = (g > 0.04045) ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
  b = (b > 0.04045) ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;

  let x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
  let y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.00000;
  let z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;

  x = (x > 0.008856) ? Math.pow(x, 1 / 3) : (7.787 * x) + 16 / 116;
  y = (y > 0.008856) ? Math.pow(y, 1 / 3) : (7.787 * y) + 16 / 116;
  z = (z > 0.008856) ? Math.pow(z, 1 / 3) : (7.787 * z) + 16 / 116;

  return { l: (116 * y) - 16, a: 500 * (x - y), b: 200 * (y - z) };
}

function calculateDeltaE(lab1, lab2) {
  return Math.sqrt(
    Math.pow(lab1.l - lab2.l, 2) +
    Math.pow(lab1.a - lab2.a, 2) +
    Math.pow(lab1.b - lab2.b, 2)
  );
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

