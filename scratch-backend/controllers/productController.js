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
  
  const { category } = req.query;
  const filter = {};
  if (category) {
    filter.$or = [
      { Category: { $regex: category, $options: 'i' } },
      { Category_Slug: { $regex: category, $options: 'i' } },
    ];
  }

  const skip = (page - 1) * limit;
  let products = await Product.find(filter).skip(skip).limit(limit).lean();
  
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
