const Order = require('../models/Order');
const Cart = require('../models/Cart');
const Product = require('../models/Product');

// Build absolute invoice URL from request object
const buildInvoiceUrl = (req, orderId) =>
  `${req.protocol}://${req.get('host')}/api/v1/orders/${orderId}/invoice`;

// POST /api/v1/orders/create  (protected)
// Body: { shippingAddress: { fullName, phone, address, city }, paymentMethod }
// Backend reads the user's server-side cart (Cart model) to populate items.
// Response: ApiResponse<OrderResponse> { success, message, data }
exports.createOrder = async (req, res) => {
  const { shippingAddress, paymentMethod } = req.body;

  if (!shippingAddress || !shippingAddress.fullName || !shippingAddress.phone || !shippingAddress.address || !shippingAddress.city) {
    return res.status(400).json({ success: false, message: 'Vui lòng điền đầy đủ địa chỉ giao hàng.' });
  }
  if (!paymentMethod) {
    return res.status(400).json({ success: false, message: 'Vui lòng chọn phương thức thanh toán.' });
  }

  // Read server-side cart
  const cart = await Cart.findOne({ userId: req.user.id });
  if (!cart || cart.items.length === 0) {
    return res.status(400).json({ success: false, message: 'Giỏ hàng trống. Vui lòng thêm sản phẩm trước khi đặt hàng.' });
  }

  // Look up product details (name, price) for each cart item
  const productIds = cart.items.map((i) => i.productId);
  const products = await Product.find({ _id: { $in: productIds } }).lean();
  const productMap = {};
  products.forEach((p) => { productMap[String(p._id)] = p; });

  const orderItems = [];
  let totalPrice = 0;

  for (const cartItem of cart.items) {
    const product = productMap[cartItem.productId];
    // Fallback price to 0 if product no longer exists (edge case)
    const unitPrice = product
      ? (product.Sale_Price > 0 ? product.Sale_Price : product.Price)
      : 0;
    const name = product ? product.Name : 'Sản phẩm không xác định';

    orderItems.push({
      product: cartItem.productId,
      name,
      quantity: cartItem.quantity,
      price: unitPrice,
      shade: cartItem.shade || '',
    });

    totalPrice += unitPrice * cartItem.quantity;
  }

  // Create order (invoiceUrl updated after save to include _id)
  const order = await Order.create({
    userId: req.user.id,
    status: 'Pending',
    totalPrice,
    paymentMethod,
    isPaid: false,
    shippingAddress,
    items: orderItems,
    invoiceUrl: '',
  });

  // Attach invoice URL now that _id is known
  order.invoiceUrl = buildInvoiceUrl(req, order._id);
  await order.save();

  // Clear server cart after successful order
  await Cart.findOneAndUpdate({ userId: req.user.id }, { items: [] });

  res.status(201).json({
    success: true,
    message: 'Đặt hàng thành công.',
    data: order.toObject(),
  });
};

// GET /api/v1/orders/my-orders  (protected)
// Response: ApiResponse<List<OrderResponse>> { success, data: [...] }
exports.getMyOrders = async (req, res) => {
  const orders = await Order.find({ userId: req.user.id }).sort({ createdAt: -1 }).lean();
  res.status(200).json({ success: true, data: orders });
};

// GET /api/v1/orders/:id  (protected)
// Response: ApiResponse<OrderResponse> { success, data }
exports.getOrderById = async (req, res) => {
  const order = await Order.findOne({
    _id: req.params.id,
    userId: req.user.id,
  }).lean();

  if (!order) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy đơn hàng.' });
  }

  res.status(200).json({ success: true, data: order });
};

// GET /api/v1/orders/:id/invoice  (protected)
// Returns a JSON invoice summary.
// DownloadManager in Android will save this as a file.
exports.getInvoice = async (req, res) => {
  const order = await Order.findOne({
    _id: req.params.id,
    userId: req.user.id,
  }).lean();

  if (!order) {
    return res.status(404).json({ success: false, message: 'Không tìm thấy đơn hàng.' });
  }

  const invoice = {
    invoiceId: `INV-${String(order._id).slice(-8).toUpperCase()}`,
    orderId: order._id,
    issuedAt: order.createdAt,
    customer: order.shippingAddress.fullName,
    phone: order.shippingAddress.phone,
    shippingAddress: `${order.shippingAddress.address}, ${order.shippingAddress.city}`,
    items: order.items.map((i) => ({
      name: i.name,
      shade: i.shade,
      quantity: i.quantity,
      unitPrice: i.price,
      subtotal: i.price * i.quantity,
    })),
    totalPrice: order.totalPrice,
    paymentMethod: order.paymentMethod,
    isPaid: order.isPaid,
    status: order.status,
  };

  // Content-Disposition: attachment so DownloadManager saves the file
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Content-Disposition', `attachment; filename="TirTir_Invoice_${String(order._id).slice(-8)}.json"`);
  res.status(200).json(invoice);
};
