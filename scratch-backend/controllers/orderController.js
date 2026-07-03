const Order = require('../models/Order');
const Cart = require('../models/Cart');
const Product = require('../models/Product');
const admin = require('firebase-admin');

// Build absolute invoice URL from request object
const buildInvoiceUrl = (req, orderId) =>
  `${req.protocol}://${req.get('host')}/api/v1/orders/${orderId}/invoice`;

// POST /api/v1/orders/create  (protected)
// Body: { shippingAddress: { fullName, phone, address, city }, paymentMethod }
// Backend reads the user's server-side cart (Cart model) to populate items.
// Response: ApiResponse<OrderResponse> { success, message, data }
exports.createOrder = async (req, res) => {
  const { shippingAddress, paymentMethod, quoteId, serviceId, idempotencyKey } = req.body;

  if (!shippingAddress || !shippingAddress.fullName || !shippingAddress.phone || !shippingAddress.address) {
    return res.status(400).json({ success: false, message: 'Vui lòng điền đầy đủ địa chỉ giao hàng.' });
  }
  if (!paymentMethod || !quoteId || !serviceId) {
    return res.status(400).json({ success: false, message: 'Thiếu phương thức thanh toán hoặc thông tin vận chuyển (Quote).' });
  }

  // Idempotency check (simple implementation)
  const existingOrder = await Order.findOne({ idempotencyKey, userId: req.user.id });
  if (existingOrder && idempotencyKey) {
     return res.status(200).json({ success: true, message: 'Đơn hàng đã được tạo trước đó.', data: existingOrder });
  }

  // Verify Quote
  const ShippingQuoteRepository = require('../shipping/ShippingQuoteRepository');
  const quoteData = ShippingQuoteRepository.get(quoteId);
  
  if (!quoteData || quoteData.userId !== req.user.id) {
     return res.status(409).json({ success: false, message: 'Báo giá vận chuyển đã hết hạn hoặc không hợp lệ. Vui lòng lấy lại phí ship.' });
  }
  
  const selectedQuote = quoteData.quotes.find(q => String(q.serviceId) === String(serviceId));
  if (!selectedQuote) {
     return res.status(400).json({ success: false, message: 'Dịch vụ vận chuyển không khớp với báo giá.' });
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

  // Add shipping fee from the validated quote
  const shippingFee = selectedQuote.fee;
  totalPrice += shippingFee;

  // Create order (invoiceUrl updated after save to include _id)
  const order = await Order.create({
    userId: req.user.id,
    status: 'Pending',
    totalPrice,
    shippingFee,
    paymentMethod,
    isPaid: false,
    shippingAddress,
    items: orderItems,
    invoiceUrl: '',
    idempotencyKey
  });

  // Attach invoice URL now that _id is known
  order.invoiceUrl = buildInvoiceUrl(req, order._id);
  await order.save();

  // Sync to Firestore (Order and Cart status completed)
  try {
    const db = admin.firestore();
    const orderDocRef = db.collection('users').doc(String(req.user.id)).collection('orders').doc(String(order._id));
    await orderDocRef.set({
      id: String(order._id),
      status: order.status,
      totalPrice: order.totalPrice,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      itemsCount: orderItems.length
    });
    // Mark cart as completed so cron ignores it
    await db.collection('carts').doc(String(req.user.id)).set({
      status: 'completed',
      items: [],
      lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  } catch (err) {
    console.error('Error syncing order/cart to Firestore:', err);
  }

  res.status(201).json({
    success: true,
    message: 'Đặt hàng thành công.',
    data: order.toObject(),
  });
};

// Helper to add timeline, cancel, and reorder fields
const enhanceOrder = (order) => {
  order.cancelable = order.status === 'Pending' || order.status === 'Processing';
  order.reorderable = true;
  order.timeline = [
    { title: 'Order Placed', time: order.createdAt, completed: true },
    { title: 'Processing', time: null, completed: order.status === 'Processing' || order.status === 'Shipped' || order.status === 'Delivered' },
    { title: 'Shipped', time: null, completed: order.status === 'Shipped' || order.status === 'Delivered' },
    { title: 'Delivered', time: null, completed: order.status === 'Delivered' }
  ];
  return order;
};

// GET /api/v1/orders/my-orders  (protected)
// Response: ApiResponse<List<OrderResponse>> { success, data: [...] }
exports.getMyOrders = async (req, res) => {
  const orders = await Order.find({ userId: req.user.id }).sort({ createdAt: -1 }).lean();
  const enhancedOrders = orders.map(enhanceOrder);
  res.status(200).json({ success: true, data: enhancedOrders });
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

  res.status(200).json({ success: true, data: enhanceOrder(order) });
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

// PATCH /api/v1/admin/orders/:id/status (or /api/v1/orders/:id/status)
exports.updateAdminOrderStatus = async (req, res) => {
  try {
    const { status } = req.body;
    const order = await Order.findById(req.params.id);
    if (!order) {
      return res.status(404).json({ success: false, message: 'Order not found' });
    }
    order.status = status;
    
    if (!order.history) order.history = [];
    order.history.push({
      status: status,
      timestamp: new Date(),
      note: req.body.note || 'Status updated by admin'
    });
    
    await order.save();

    // Sync to Firestore
    try {
      const db = admin.firestore();
      const orderDocRef = db.collection('users').doc(String(order.userId)).collection('orders').doc(String(order._id));
      await orderDocRef.set({
        status: order.status,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
    } catch (err) {
      console.error('Error syncing order status update to Firestore:', err);
    }

    res.status(200).json({ success: true, data: order });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// GET /api/v1/admin/orders/:id
exports.getAdminOrderDetails = async (req, res) => {
  try {
    const order = await Order.findById(req.params.id).populate('userId', 'firstName lastName email phoneNumber');
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });
    res.status(200).json({ success: true, data: order });
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// PATCH /api/v1/admin/orders/:id/notes
exports.updateAdminNotes = async (req, res) => {
  try {
    const { adminNotes } = req.body;
    const order = await Order.findByIdAndUpdate(req.params.id, { adminNotes }, { new: true });
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });
    res.status(200).json({ success: true, data: order });
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// PATCH /api/v1/admin/orders/:id/shipping
exports.updateShippingDetails = async (req, res) => {
  try {
    const { trackingNumber, carrier, estimatedDeliveryDate } = req.body;
    const order = await Order.findById(req.params.id);
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });
    
    order.shippingDetails = {
      trackingNumber: trackingNumber || order.shippingDetails?.trackingNumber,
      carrier: carrier || order.shippingDetails?.carrier,
      estimatedDeliveryDate: estimatedDeliveryDate || order.shippingDetails?.estimatedDeliveryDate
    };
    await order.save();
    res.status(200).json({ success: true, data: order });
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// POST /api/v1/admin/orders/:id/cancel
exports.cancelOrderAdmin = async (req, res) => {
  try {
    const { cancellationReason } = req.body;
    if (!cancellationReason) return res.status(400).json({ success: false, message: 'Cancellation reason is required' });
    
    const order = await Order.findById(req.params.id);
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });
    
    order.status = 'Cancelled';
    order.cancellationReason = cancellationReason;
    if (!order.history) order.history = [];
    order.history.push({
      status: 'Cancelled',
      timestamp: new Date(),
      note: `Cancelled by admin. Reason: ${cancellationReason}`
    });
    
    await order.save();
    
    // Sync to Firestore
    try {
      const db = admin.firestore();
      const orderDocRef = db.collection('users').doc(String(order.userId)).collection('orders').doc(String(order._id));
      await orderDocRef.set({ status: 'Cancelled', updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
    } catch (err) { console.error('Error syncing order status update to Firestore:', err); }
    
    res.status(200).json({ success: true, data: order });
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// GET /api/v1/admin/orders/export/csv
exports.exportOrdersCsv = async (req, res) => {
  try {
    const orders = await Order.find({}).populate('userId', 'email').sort({ createdAt: -1 });
    let csv = 'Order ID,Date,User Email,Total Price,Payment Method,Status,Tracking Number,Carrier\n';
    
    orders.forEach(order => {
      const id = order._id;
      const date = order.createdAt ? order.createdAt.toISOString() : '';
      const email = (order.userId && order.userId.email) ? order.userId.email : 'Unknown';
      const total = order.totalPrice;
      const pm = order.paymentMethod;
      const status = order.status;
      const tracking = (order.shippingDetails && order.shippingDetails.trackingNumber) ? order.shippingDetails.trackingNumber : '';
      const carrier = (order.shippingDetails && order.shippingDetails.carrier) ? order.shippingDetails.carrier : '';
      
      csv += `${id},${date},${email},${total},${pm},${status},${tracking},${carrier}\n`;
    });
    
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="tirtir_orders_export.csv"');
    res.status(200).send(csv);
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// GET /api/v1/admin/orders/:id
exports.getAdminOrderDetails = async (req, res) => {
  try {
    const order = await Order.findById(req.params.id)
      .populate('userId', 'email firstName lastName')
      .populate('items.product', 'Name Category');
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });
    res.status(200).json({ success: true, data: order });
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// PATCH /api/v1/admin/orders/:id/notes
exports.updateAdminNotes = async (req, res) => {
  try {
    const { adminNotes } = req.body;
    const order = await Order.findByIdAndUpdate(req.params.id, { adminNotes }, { new: true });
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });
    res.status(200).json({ success: true, data: order });
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// PATCH /api/v1/admin/orders/:id/shipping
exports.updateShippingDetails = async (req, res) => {
  try {
    const { carrier, trackingNumber, estimatedDeliveryDate, status } = req.body;
    const update = {};
    if (carrier !== undefined) update['shippingDetails.carrier'] = carrier;
    if (trackingNumber !== undefined) update['shippingDetails.trackingNumber'] = trackingNumber;
    if (estimatedDeliveryDate !== undefined) update['shippingDetails.estimatedDeliveryDate'] = estimatedDeliveryDate;
    if (status !== undefined) update.status = status;
    
    const order = await Order.findByIdAndUpdate(req.params.id, { $set: update }, { new: true });
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });
    res.status(200).json({ success: true, data: order });
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// POST /api/v1/admin/orders/:id/cancel
exports.cancelOrderAdmin = async (req, res) => {
  try {
    const { cancellationReason } = req.body;
    const order = await Order.findById(req.params.id);
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });
    
    order.status = 'Cancelled';
    order.cancellationReason = cancellationReason || 'No reason provided';
    order.adminNotes = (order.adminNotes || '') + '\n[Cancelled by Admin]';
    await order.save();
    
    res.status(200).json({ success: true, data: order });
  } catch (err) {
    res.status(500).json({ success: false, message: 'Server error' });
  }
};
