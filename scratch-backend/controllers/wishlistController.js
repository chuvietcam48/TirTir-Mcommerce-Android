const Wishlist = require('../models/Wishlist');
const Product = require('../models/Product');

// GET /api/v1/wishlist
exports.getWishlist = async (req, res) => {
  let wishlist = await Wishlist.findOne({ userId: req.user.id });
  if (!wishlist) {
    wishlist = await Wishlist.create({ userId: req.user.id, products: [] });
  }

  // Populate product details
  const products = await Product.find({
    $or: [
      { _id: { $in: wishlist.products.filter(id => id.length === 24) } },
      { Product_ID: { $in: wishlist.products } }
    ]
  }).lean();

  res.status(200).json({ success: true, data: products });
};

// POST /api/v1/wishlist/sync
// Body: { products: ['productId1', 'productId2'] }
exports.syncWishlist = async (req, res) => {
  const { products } = req.body;

  if (!Array.isArray(products)) {
    return res.status(400).json({ success: false, message: 'Products phải là một mảng.' });
  }

  let wishlist = await Wishlist.findOne({ userId: req.user.id });
  if (!wishlist) {
    wishlist = await Wishlist.create({ userId: req.user.id, products: [] });
  }

  // Merge products
  const productSet = new Set([...wishlist.products, ...products]);
  wishlist.products = Array.from(productSet);
  await wishlist.save();

  res.status(200).json({ success: true, message: 'Đồng bộ wishlist thành công', data: wishlist.products });
};
