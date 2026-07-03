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

  try {
    const admin = require('firebase-admin');
    await admin.firestore().collection('carts').doc(String(req.user.id)).set({
      userId: String(req.user.id),
      items: cart.items,
      status: 'active',
      lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
      recoveryNotified: 0
    }, { merge: true });
  } catch (err) {
    console.error('Error syncing cart to Firestore:', err);
  }

  res.status(200).end(); // CartRepository expects Call<Void> — no body required
};

// GET /api/v1/cart  (protected)
// Response: ApiResponse<Void> — client uses SQLite as primary source
exports.getCart = async (req, res) => {
  const cart = await Cart.findOne({ userId: req.user.id }).lean();
  res.status(200).json({ success: true, data: cart ? cart.items : [] });
};

// PUT /api/v1/cart/update (Cập nhật số lượng)
// Body: { productId, quantity }
exports.updateCartServer = async (req, res) => {
  const { productId, quantity } = req.body;
  if (!productId || quantity === undefined) {
    return res.status(400).json({ success: false, message: 'Thiếu productId hoặc quantity.' });
  }

  const cart = await Cart.findOne({ userId: req.user.id });
  if (!cart) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy giỏ hàng.' });
  }

  const itemIndex = cart.items.findIndex(item => item.productId === productId);
  if (itemIndex >= 0) {
    if (quantity > 0) {
      cart.items[itemIndex].quantity = quantity;
    } else {
      cart.items.splice(itemIndex, 1); // Xóa nếu quantity = 0
    }
    await cart.save();

    try {
      const admin = require('firebase-admin');
      await admin.firestore().collection('carts').doc(String(req.user.id)).set({
        userId: String(req.user.id),
        items: cart.items,
        status: cart.items.length > 0 ? 'active' : 'completed',
        lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
    } catch (err) {
      console.error('Error syncing cart update to Firestore:', err);
    }
  }
  
  res.status(200).json({ success: true, message: 'Cập nhật giỏ hàng thành công.' });
};

// DELETE /api/v1/cart/clear (Xóa sạch giỏ hàng)
exports.clearCartServer = async (req, res) => {
  const cart = await Cart.findOne({ userId: req.user.id });
  if (cart) {
    cart.items = [];
    await cart.save();
  }

  try {
    const admin = require('firebase-admin');
    await admin.firestore().collection('carts').doc(String(req.user.id)).set({
      userId: String(req.user.id),
      items: [],
      status: 'completed',
      lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  } catch (err) {
    console.error('Error syncing cart clear to Firestore:', err);
  }
  res.status(200).json({ success: true, message: 'Đã xóa sạch giỏ hàng.' });
};

// POST /api/v1/cart/sync (Đồng bộ giỏ hàng)
// Body: { items: [{ productId, quantity, shade }] }
// Strategy: Client-wins (overwrite server state with local SQLite state)
exports.syncCart = async (req, res) => {
  const { items } = req.body;
  if (!Array.isArray(items)) {
    return res.status(400).json({ success: false, message: 'Danh sách sản phẩm không hợp lệ' });
  }

  let cart = await Cart.findOne({ userId: req.user.id });
  if (!cart) {
    cart = new Cart({ userId: req.user.id, items: [] });
  }

  // Overwrite existing items with local items (Client-wins)
  cart.items = items;
  await cart.save();

  try {
    const admin = require('firebase-admin');
    await admin.firestore().collection('carts').doc(String(req.user.id)).set({
      userId: String(req.user.id),
      items: cart.items,
      status: cart.items.length > 0 ? 'active' : 'completed',
      lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  } catch (err) {
    console.error('Error syncing cart sync to Firestore:', err);
  }

  res.status(200).json({ success: true, data: cart.items });
};
