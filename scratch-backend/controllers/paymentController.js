/**
 * paymentController.js
 * ──────────────────────────────────────────────────────────────────────────
 * POST /api/v1/payments/arbitrate
 *   1. Fetch shipping via Viettel Post SOAP (5-second race/fallback)
 *   2. Calculate authoritative totals: Subtotal + Tax(10%) + Shipping - Discount
 *   3. Validate voucher (if provided)
 *   4. Create Order in MongoDB with status "pending_payment"
 *   5. Build VNPAY payment URL (SHA-512 signed)
 *   6. Return { paymentUrl, orderId, totals, isEstimatedShipping }
 *
 * POST /api/v1/payments/vnpay-return   (browser redirect after payment)
 * POST /api/v1/payments/vnpay-ipn      (VNPAY server-to-server notification)
 *   → On success: update Order.isPaid=true, status="Processing", sync Firestore
 */

const Order          = require('../models/Order');
const Cart           = require('../models/Cart');
const Product        = require('../models/Product');
const admin          = require('firebase-admin');
const { getShippingFee }          = require('../utils/shippingService');
const { buildVnpayUrl, verifyVnpayCallback } = require('../utils/vnpayHelper');

const VAT_RATE = 0.10; // 10% VAT

// ─── Utility: sync an order to Firestore realtime feed ──────────────────────
async function syncOrderToFirestore(order) {
  try {
    const db = admin.firestore();
    const snapshot = await db.collection('users')
      .where('backendUserId', '==', String(order.userId))
      .limit(1)
      .get();
    if (snapshot.empty) return;
    const firebaseUid = snapshot.docs[0].id;
    await db.collection('users').doc(firebaseUid)
      .collection('orders').doc(String(order._id))
      .set({
        id:           String(order._id),
        status:       order.status,
        isPaid:       order.isPaid,
        totalPrice:   order.totalPrice,
        updatedAt:    admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
  } catch (err) {
    console.error('[Firestore] sync failed:', err.message);
  }
}

// ─── POST /api/v1/payments/arbitrate ────────────────────────────────────────
exports.arbitrate = async (req, res) => {
  try {
    const { shippingAddress, paymentMethod, voucherCode, toProvince, quoteId, serviceId } = req.body;

    // 1. Validate required fields (with fallback for mock accounts)
    shippingAddress.fullName = shippingAddress.fullName || req.user.name || 'Khách hàng';
    shippingAddress.phone = shippingAddress.phone || req.user.phone || '0901234567';
    shippingAddress.address = shippingAddress.address || 'Địa chỉ mặc định';
    shippingAddress.city = shippingAddress.city || 'Hồ Chí Minh';
    
    if (!shippingAddress.fullName || !shippingAddress.phone ||
        !shippingAddress.address  || !shippingAddress.city) {
      return res.status(400).json({ success: false, message: 'Vui lòng điền đầy đủ địa chỉ giao hàng.' });
    }
    const validPaymentMethods = ['VNPAY', 'MOMO', 'CARD', 'COD'];
    if (!validPaymentMethods.includes(paymentMethod)) {
      return res.status(400).json({ success: false, message: 'Phương thức thanh toán không hợp lệ.' });
    }

    // 2. Read server-side cart
    const cart = await Cart.findOne({ userId: req.user.id });
    if (!cart || cart.items.length === 0) {
      return res.status(400).json({ success: false, message: 'Giỏ hàng trống.' });
    }

    // 3. Look up product prices (server-authoritative — no trust of client prices)
    const productIds = cart.items.map((i) => i.productId);
    
    // Separate ObjectIds and custom String IDs (e.g., TR-001)
    const mongoose = require('mongoose');
    const objectIds = [];
    const customIds = [];
    
    productIds.forEach(id => {
      if (mongoose.Types.ObjectId.isValid(id) && String(new mongoose.Types.ObjectId(id)) === String(id)) {
        objectIds.push(id);
      } else {
        customIds.push(id);
      }
    });

    const products = await Product.find({
      $or: [
        { _id: { $in: objectIds } },
        { Product_ID: { $in: customIds } },
        { Product_ID: { $in: objectIds } }
      ]
    }).lean();

    const productMap = {};
    products.forEach((p) => { 
      productMap[String(p._id)] = p; 
      if (p.Product_ID) productMap[p.Product_ID] = p; 
    });

    const orderItems = [];
    let   subtotal   = 0;

    for (const cartItem of cart.items) {
      const p         = productMap[cartItem.productId];
      const unitPrice = p ? (p.Sale_Price > 0 ? p.Sale_Price : p.Price) : 0;
      const name      = p ? p.Name : 'Sản phẩm không xác định';
      orderItems.push({ product: cartItem.productId, name, quantity: cartItem.quantity, price: unitPrice, shade: cartItem.shade || '' });
      subtotal += unitPrice * cartItem.quantity;
    }

    // 4. Shipping via Viettel Post SOAP (5 s race)
    let shippingFee = 2.00;
    let isEstimatedShipping = false;
    if (quoteId !== 'manual_quote') {
      const shippingResult = await getShippingFee({
        toProvince:  toProvince || shippingAddress.city,
        weightGrams: 300 * cart.items.reduce((sum, i) => sum + i.quantity, 0),
        totalPrice:  subtotal,
      });
      shippingFee = shippingResult.fee;
      isEstimatedShipping = shippingResult.isEstimated;
    }

    // 5. Authoritative total calculation
    const tax         = subtotal * VAT_RATE;
    let   discount    = 0;
    let   voucherMsg  = null;

    if (voucherCode) {
      const code = String(voucherCode).toUpperCase().trim();
      if (code === 'TIRTIR_ROUTINE_5') {
        discount   = subtotal * 0.05;
        voucherMsg = 'Mã giảm giá 5% đã được áp dụng thành công.';
      } else {
        // Future: look up Voucher collection here
        voucherMsg = 'Mã giảm giá không hợp lệ hoặc đã hết hạn.';
      }
    }

    const finalTotal = subtotal + shippingFee + tax - discount;

    // 6. Create pending order in MongoDB
    const order = await Order.create({
      userId:          req.user.id,
      status:          'Pending',           // will move to Processing after payment
      totalPrice:      finalTotal,
      paymentMethod,
      isPaid:          false,
      shippingAddress,
      items:           orderItems,
      invoiceUrl:      '',
    });

    // Attach invoice URL
    order.invoiceUrl = `${req.protocol}://${req.get('host')}/api/v1/orders/${order._id}/invoice`;
    await order.save();

    // 7. Clear server cart
    await Cart.findOneAndUpdate({ userId: req.user.id }, { items: [] });
    try {
      await db.collection('carts').doc(String(req.user.id)).set({ status: 'completed', items: [], lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
    } catch (e) {
      console.error('Failed to clear firestore cart:', e);
    }

    // 8. Sync initial state to Firestore
    await syncOrderToFirestore(order);

    // 9. Build payment URL (VNPAY only for now; MOMO / COD handled separately)
    let paymentUrl = null;
    if (paymentMethod === 'VNPAY') {
      const ipAddr = req.headers['x-forwarded-for'] || req.socket?.remoteAddress || '127.0.0.1';
      const amountVnd = finalTotal < 1000 ? finalTotal * 25000 : finalTotal;
      paymentUrl   = buildVnpayUrl(order._id, amountVnd, ipAddr, `TirTir Order ${order._id}`);
    }

    return res.status(201).json({
      success: true,
      message: 'Đơn hàng tạm đã được tạo. Vui lòng hoàn tất thanh toán.',
      data: {
        orderId:              String(order._id),
        paymentUrl,
        invoiceUrl:           order.invoiceUrl,
        isEstimatedShipping,
        voucherMessage:       voucherMsg,
        totals: {
          subtotal:   Math.round(subtotal),
          shippingFee:Math.round(shippingFee),
          tax:        Math.round(tax),
          discount:   Math.round(discount),
          finalTotal: Math.round(finalTotal),
        },
      },
    });
  } catch (err) {
    console.error('[arbitrate] Error:', err);
    res.status(500).json({ success: false, message: 'Lỗi máy chủ nội bộ.' });
  }
};

// ─── GET /api/v1/payments/vnpay-return  (browser redirect) ──────────────────
exports.vnpayReturn = async (req, res) => {
  const { valid, success, txnRef } = verifyVnpayCallback(req.query);
  if (!valid) {
    return res.status(400).json({ success: false, message: 'Chữ ký không hợp lệ.' });
  }
  if (success && txnRef) {
    const order = await Order.findByIdAndUpdate(txnRef,
      { isPaid: true, status: 'Processing' }, { new: true });
    if (order) await syncOrderToFirestore(order);
  }
  // Redirect to deep-link handled by Android (custom scheme)
  const status = success ? 'success' : 'failed';
  return res.redirect(`tirtir://payment?status=${status}&orderId=${txnRef}`);
};

// ─── POST /api/v1/payments/vnpay-ipn  (VNPAY server-to-server) ──────────────
exports.vnpayIpn = async (req, res) => {
  try {
    const { valid, success, txnRef } = verifyVnpayCallback(req.query);

    if (!valid) {
      // VNPAY requires a specific response format
      return res.status(200).json({ RspCode: '97', Message: 'Invalid checksum' });
    }

    const order = await Order.findById(txnRef);
    if (!order) {
      return res.status(200).json({ RspCode: '01', Message: 'Order not found' });
    }
    if (order.isPaid) {
      return res.status(200).json({ RspCode: '02', Message: 'Order already updated' });
    }

    if (success) {
      order.isPaid  = true;
      order.status  = 'Processing';
      await order.save();
      await syncOrderToFirestore(order);
    }

    return res.status(200).json({ RspCode: '00', Message: 'Confirm Success' });
  } catch (err) {
    console.error('[vnpay-ipn] Error:', err);
    return res.status(200).json({ RspCode: '99', Message: 'Internal error' });
  }
};
