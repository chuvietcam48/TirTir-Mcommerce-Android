const Cart = require('../models/Cart');

// POST /api/v1/cart/add  (protected)
// Body: { productId, quantity, shade }
// Called by CartRepository.syncItemToServer() — returns 200 OK (no body needed)
exports.addToCart = async (req, res) => {
  const { productId, quantity = 1, shade = '' } = req.body;

  if (!productId) {
    return res.status(400).json({ success: false, message: 'productId là bắt buộc.' });
  }

  const qty = Math.max(1, parseInt(quantity, 10) || 1);

  let cart = await Cart.findOne({ userId: req.user.id });

  if (!cart) {
    cart = new Cart({ userId: req.user.id, items: [] });
  }

  const existingIdx = cart.items.findIndex((i) => i.productId === productId);
  if (existingIdx >= 0) {
    cart.items[existingIdx].quantity += qty;
    if (shade) cart.items[existingIdx].shade = shade;
  } else {
    cart.items.push({ productId, quantity: qty, shade });
  }

  await cart.save();
  res.status(200).end(); // CartRepository expects Call<Void> — no body required
};

// GET /api/v1/cart  (protected)
// Response: ApiResponse<Void> — client uses SQLite as primary source
exports.getCart = async (req, res) => {
  const cart = await Cart.findOne({ userId: req.user.id }).lean();
  res.status(200).json({ success: true, data: cart ? cart.items : [] });
};
