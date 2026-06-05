const Order = require('../models/order.model');
const Cart = require('../models/cart.model');
const Product = require('../models/product.model');
const StockHistory = require('../models/stock.history.model');
const mongoose = require('mongoose');
const { ORDER_STATUS } = require('../constants');
const { createNotification } = require('./notification.controller');
const { emitToAdmins } = require('../services/socket.service');
// Lazy-load to avoid circular dep: shipping.controller → order.model ← order.controller
const getShippingController = () => require('./shipping.controller');

// Currency formatter for socket event messages
const formatCurrency = (n) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n);

const appendStatusHistory = (order, status, note = '') => {
    const history = Array.isArray(order.statusHistory) ? order.statusHistory : [];
    const last = history[history.length - 1];
    if (last?.status === status) return;
    history.push({
        status,
        timestamp: new Date(),
        note: (note || '').trim()
    });
    order.statusHistory = history;
};

// ─── Helper: check if sessions are supported (Replica Set / Atlas only) ────────
let _sessionSupported = null; // null = not yet checked, true/false = cached result
const isSessionSupported = async () => {
    if (_sessionSupported !== null) return _sessionSupported;
    try {
        const session = await mongoose.startSession();
        await session.endSession();
        _sessionSupported = true;
    } catch {
        _sessionSupported = false;
    }
    return _sessionSupported;
};

// 1. TẠO ĐƠN HÀNG (CHECKOUT)
// Uses MongoDB Transactions (Replica Set / Atlas) with graceful fallback to
// atomic findOneAndUpdate for standalone MongoDB (dev/demo without replica set).
exports.createOrder = async (req, res) => {
    const useTransactions = await isSessionSupported();

    if (useTransactions) {
        // ── TRANSACTION PATH (Atlas / Replica Set) ──────────────────────────────
        const session = await mongoose.startSession();
        session.startTransaction();
        try {
            const result = await _createOrderLogic(req, session);
            await session.commitTransaction();
            session.endSession();
            return res.status(201).json({ message: "Đặt hàng thành công!", orderId: result });
        } catch (error) {
            await session.abortTransaction();
            session.endSession();
            console.error("Create Order Error (TX):", error);
            return res.status(error.statusCode || 500).json({ message: error.message || "Lỗi Server khi tạo đơn hàng.", errorCode: error.code || 'SERVER_ERROR' });
        }
    } else {
        // ── FALLBACK PATH (Standalone MongoDB) ─────────────────────────────────
        try {
            const result = await _createOrderLogic(req, null);
            return res.status(201).json({ message: "Đặt hàng thành công!", orderId: result });
        } catch (error) {
            console.error("Create Order Error (Fallback):", error);
            return res.status(error.statusCode || 500).json({ message: error.message || "Lỗi Server khi tạo đơn hàng.", errorCode: error.code || 'SERVER_ERROR' });
        }
    }
};

// ─── Shared order-creation logic (works with or without session) ─────────────
async function _createOrderLogic(req, session) {
    const userId = req.user.id;
    const { shippingAddress, paymentMethod, saveToAddressBook } = req.body;

    const cartQuery = Cart.findOne({ user: userId }).populate('items.product');
    const cart = session ? await cartQuery.session(session) : await cartQuery;

    if (!cart || cart.items.length === 0) {
        const err = new Error("Giỏ hàng trống!"); err.statusCode = 400; err.code = 'CART_EMPTY'; throw err;
    }

    const orderItems = [];
    let calculatedTotal = 0;
    const reservedProducts = [];

    for (const item of cart.items) {
        if (!item.product) continue;

        const opts = session ? { new: true, session } : { new: true };
        const updatedProduct = await Product.findOneAndUpdate(
            { _id: item.product._id, Stock_Quantity: { $gte: item.quantity } },
            { $inc: { Stock_Quantity: -item.quantity, Stock_Reserved: item.quantity } },
            opts
        );

        if (!updatedProduct) {
            // Rollback previously reserved items (only needed in fallback path)
            if (!session) {
                for (const r of reservedProducts) {
                    await Product.findByIdAndUpdate(r.id, {
                        $inc: { Stock_Quantity: r.qty, Stock_Reserved: -r.qty }
                    });
                }
            }
            const err = new Error(`Hết hàng hoặc không đủ số lượng cho: ${item.product.Name}`);
            err.statusCode = 400; err.code = 'INSUFFICIENT_STOCK'; err.productName = item.product.Name; throw err;
        }

        reservedProducts.push({ id: item.product._id, qty: item.quantity, price: item.product.Price });
        orderItems.push({
            product: item.product._id,
            name: item.product.Name,
            quantity: item.quantity,
            price: item.product.Price,
            shade: item.shade,
            image: item.product.Thumbnail_Images
        });
        calculatedTotal += item.product.Price * item.quantity;
    }

    let recoveredFrom;
    if (cart.recoveryStatus && ['email_1_sent', 'email_2_sent', 'email_3_sent'].includes(cart.recoveryStatus)) {
        recoveredFrom = cart.recoveryStatus.replace('_sent', ''); // Map to 'email_1', 'email_2', 'email_3'
    }

    const newOrder = new Order({
        user: userId, items: orderItems, shippingAddress, paymentMethod,
        totalAmount: calculatedTotal, status: ORDER_STATUS.PENDING,
        statusHistory: [{ status: ORDER_STATUS.PENDING, timestamp: new Date(), note: 'Order placed' }],
        ...(recoveredFrom && { recoveredFrom })
    });

    const savedOrder = session ? await newOrder.save({ session }) : await newOrder.save();

    // CANCEL Cart Recovery Jobs since the user has checked out
    try {
        const { cancelRecoveryJobsForCart } = require('../queues/cart_recovery.queue');
        await cancelRecoveryJobsForCart(cart._id.toString());
    } catch (err) {
        console.error("Failed to cancel recovery jobs natively", err);
    }

    for (const reserved of reservedProducts) {
        const historyDoc = [{
            product: reserved.id, action: 'Reserve', change_type: 'Decrease',
            source_id: savedOrder._id.toString(), balance_before: 0, balance_after: 0,
            changeAmount: reserved.qty, reason: 'Order Placed (Reserved)', performedBy: userId
        }];
        session
            ? await StockHistory.create(historyDoc, { session })
            : await StockHistory.create(historyDoc[0]);
    }

    const cartDeleteQuery = Cart.findOneAndDelete({ user: userId });
    session ? await cartDeleteQuery.session(session) : await cartDeleteQuery;

    if (saveToAddressBook && shippingAddress) {
        const User = require('../models/user.model');
        const updateAddressQuery = User.findByIdAndUpdate(userId, {
            $push: {
                addresses: {
                    fullName: shippingAddress.fullName,
                    phone: shippingAddress.phone,
                    street: shippingAddress.address,
                    city: shippingAddress.city,
                    district: shippingAddress.district,
                    ward: shippingAddress.ward,
                    isDefault: false
                }
            }
        });
        session ? await updateAddressQuery.session(session) : await updateAddressQuery;
    }

    // ── Real-time: notify admins of new order ─────────────────────────────────
    emitToAdmins('new_order', {
        type: 'new_order',
        title: 'New Order',
        message: `Order #${savedOrder._id.toString().slice(-6).toUpperCase()} — ${formatCurrency(savedOrder.totalAmount)}`,
        orderId: savedOrder._id
    });

    // Notify user: order placed
    const placedShortId = savedOrder._id.toString().slice(-6).toUpperCase();
    await createNotification(
        userId,
        'order',
        'Order Placed',
        `Your order #${placedShortId} has been placed successfully and is awaiting confirmation.`,
        `/account/orders/${savedOrder._id}`
    );

    return savedOrder._id;
}


// 2. LẤY DANH SÁCH ĐƠN HÀNG CỦA USER
exports.getMyOrders = async (req, res) => {
    try {
        // Get userId from authenticated user (set by protect middleware)
        const userId = req.user.id;

        // Sort: { createdAt: -1 } nghĩa là giảm dần (mới nhất nằm trên cùng)
        const orders = await Order.find({ user: userId })
            .sort({ createdAt: -1 });

        res.json(orders);
    } catch (error) {
        console.error("Get Orders Error:", error);
        res.status(500).json({ message: "Lỗi khi lấy danh sách đơn hàng" });
    }
};

// 3. CHI TIẾT ĐƠN HÀNG
exports.getOrderById = async (req, res) => {
    try {
        const order = await Order.findById(req.params.id)
            .populate('user', 'name email')
            .populate('items.product', 'Name Thumbnail_Images Price Product_ID slug')
            .lean();

        if (!order) {
            return res.status(404).json({ message: "Không tìm thấy đơn hàng" });
        }

        // Security: Check if the order belongs to the requesting user (unless admin)
        const orderUserId = order.user ? (order.user._id || order.user).toString() : null;
        if (orderUserId !== req.user.id && req.user.role !== 'admin') {
            return res.status(403).json({ message: "Bạn không có quyền xem đơn hàng này" });
        }

        res.json(order);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// 4. CẬP NHẬT TRẠNG THÁI (Dành cho Admin)
// Flow: Pending -> Processing -> Shipped -> Delivered
exports.updateOrderStatus = async (req, res) => {
    try {
        const { orderId, status } = req.body;

        const validStatuses = Object.values(ORDER_STATUS);
        if (!validStatuses.includes(status)) {
            return res.status(400).json({ message: "Trạng thái không hợp lệ" });
        }

        const order = await Order.findById(orderId).populate('items.product');

        if (!order) {
            return res.status(404).json({ message: "Không tìm thấy đơn hàng" });
        }

        const oldStatus = order.status;

        // --- STOCK MANAGEMENT LOGIC ---

        // 1. Pending -> Processing: CONFIRM Stock (Deduct from Reserved)
        // Note: Stock was already deducted from Stock_Quantity when Order was placed (Pending).
        // It is currently in Stock_Reserved. We just need to remove it from Stock_Reserved.
        if (status === 'Processing' && oldStatus === 'Pending') {
            for (const item of order.items) {
                // Confirm Sale: Remove from Reserved
                const product = await Product.findByIdAndUpdate(item.product._id, {
                    $inc: { Stock_Reserved: -item.quantity }
                }, { new: true });

                // Log History (Sale)
                if (product) {
                    await StockHistory.create({
                        product: product._id,
                        action: 'Sale',
                        change_type: 'Decrease', // Technically no change to Available Stock, but leaving system
                        source_id: order._id.toString(),
                        balance_before: product.Stock_Quantity, // Available stays same
                        balance_after: product.Stock_Quantity,
                        changeAmount: item.quantity,
                        reason: 'Order Confirmed (Reserved Released)',
                        performedBy: req.user.id
                    });
                }
            }
        }

        // STOCK STATE MACHINE:
        // Order PENDING   → Stock_Reserved++, Stock_Quantity--  (reserved on order create)
        // Order CONFIRMED → Stock_Reserved--,  Stock_Quantity unchanged  (stock already deducted)
        // Order CANCELLED → Stock_Reserved-- (release), Stock_Quantity++  (return to available)

        // 2. Pending -> Cancelled: RELEASE Stock (Reserved -> Available)
        if (status === 'Cancelled' && oldStatus === 'Pending') {
            for (const item of order.items) {
                // Return to Stock
                const product = await Product.findByIdAndUpdate(item.product._id, {
                    $inc: {
                        Stock_Quantity: item.quantity,
                        Stock_Reserved: -item.quantity
                    }
                }, { new: true });

                if (product) {
                    await StockHistory.create({
                        product: product._id,
                        action: 'Release',
                        change_type: 'Increase',
                        source_id: order._id.toString(),
                        balance_before: product.Stock_Quantity - item.quantity,
                        balance_after: product.Stock_Quantity,
                        changeAmount: item.quantity,
                        reason: 'Order Cancelled (Stock Returned)',
                        performedBy: req.user.id
                    });
                }
            }

            // Guard: clamp Stock_Reserved to 0 — prevents negative drift from race conditions
            await Product.updateMany(
                { _id: { $in: order.items.map(i => i.product._id) }, Stock_Reserved: { $lt: 0 } },
                { $set: { Stock_Reserved: 0 } }
            );
        }

        // STOCK STATE MACHINE:
        // Order PENDING   → Stock_Reserved++, Stock_Quantity--  (reserved on order create)
        // Order CONFIRMED → Stock_Reserved--,  Stock_Quantity unchanged  (stock already deducted)
        // Order CANCELLED → Stock_Reserved-- (release), Stock_Quantity++  (return to available)

        // 3. Processing/Shipped -> Cancelled: RESTOCK (Refund)
        // Stock was already fully deducted (Reserved cleared at Processing). Add back to Available only.
        if (status === 'Cancelled' && (oldStatus === 'Processing' || oldStatus === 'Shipped')) {
            for (const item of order.items) {
                const product = await Product.findByIdAndUpdate(item.product._id, {
                    $inc: { Stock_Quantity: item.quantity }
                }, { new: true });

                if (product) {
                    await StockHistory.create({
                        product: product._id,
                        action: 'Refund',
                        change_type: 'Increase',
                        source_id: order._id.toString(),
                        balance_before: product.Stock_Quantity - item.quantity,
                        balance_after: product.Stock_Quantity,
                        changeAmount: item.quantity,
                        reason: 'Order Cancelled (Restock)',
                        performedBy: req.user.id
                    });
                }
            }

            // Guard: clamp Stock_Reserved to 0 — safety net against any stale reservation
            await Product.updateMany(
                { _id: { $in: order.items.map(i => i.product._id) }, Stock_Reserved: { $lt: 0 } },
                { $set: { Stock_Reserved: 0 } }
            );
        }

        // --- END STOCK LOGIC ---

        // ─── GHN: Create Shipping Order when Admin marks Shipped ─────────────
        if (status === 'Shipped' && oldStatus === 'Processing') {
            try {
                const { createGHNOrder } = getShippingController();
                const ghnCode = await createGHNOrder(order);
                if (ghnCode) order.ghnOrderCode = ghnCode; // make sure save() includes it
            } catch (ghnErr) {
                // GHN failed → leave order at Processing (createGHNOrder already rolled back status)
                return res.status(ghnErr.statusCode || 502).json({
                    message: ghnErr.message,
                    hint: 'Order status reverted to Processing. Please try again or create GHN shipment manually.'
                });
            }
        }

        order.status = status;
        // Keep statusHistory in sync with current status, especially Delivered.
        appendStatusHistory(order, status, req.body.note || '');
        const updatedOrder = await order.save();

        // ─── Gửi notification cho chủ đơn hàng ────────────────────────────────
        const orderIdStr = order._id.toString();
        const shortId = orderIdStr.slice(-6).toUpperCase();
        const notifMap = {
            Processing: {
                title: 'Order Processing',
                message: `Order #${shortId} has been confirmed and is being prepared.`
            },
            Shipped: {
                title: 'Order Shipped',
                message: `Order #${shortId} has been handed to the delivery carrier and is on its way.`
            },
            Delivered: {
                title: 'Order Delivered',
                message: `Order #${shortId} has been delivered successfully. Thank you for shopping with us!`
            },
            Cancelled: {
                title: 'Order Cancelled',
                message: `Order #${shortId} has been cancelled. Please contact support if you need help.`
            }
        };

        if (notifMap[status]) {
            const userIdToNotify = order.user?._id || order.user;
            await createNotification(
                userIdToNotify,
                'order',
                notifMap[status].title,
                notifMap[status].message,
                `/account/orders/${order._id}`
            );
        }
        // ──────────────────────────────────────────────────────────────────────

        res.json({ message: "Cập nhật trạng thái thành công", order: updatedOrder });
    } catch (error) {
        console.error("Update Order Error:", error);
        res.status(500).json({ message: error.message });
    }
};
// 5. CẬP NHẬT FULFILLMENT (Admin)
exports.updateFulfillment = async (req, res) => {
    try {
        const { id } = req.params;
        const { carrier, trackingNumber, isPacked } = req.body;

        const order = await Order.findById(id);
        if (!order) return res.status(404).json({ message: 'Không tìm thấy đơn hàng' });

        if (carrier !== undefined) order.carrier = carrier;
        if (trackingNumber !== undefined) order.trackingNumber = trackingNumber;
        if (isPacked !== undefined) order.isPacked = isPacked;

        await order.save();
        res.json({ message: 'Fulfillment đã được cập nhật', order });
    } catch (error) {
        console.error('Update Fulfillment Error:', error);
        res.status(500).json({ message: error.message });
    }
};

// 6. HỦY ĐƠN HÀNG (User)
exports.cancelOrder = async (req, res) => {
    try {
        const orderId = req.params.id;
        const userId = req.user.id; // Sau này lấy từ Token

        const order = await Order.findOne({ _id: orderId, user: userId });

        if (!order) {
            return res.status(404).json({ message: "Không tìm thấy đơn hàng" });
        }

        // Chỉ cho phép hủy khi trạng thái là Pending
        if (order.status !== 'Pending') {
            return res.status(400).json({
                message: "Không thể hủy đơn hàng này vì đã được xử lý hoặc vận chuyển."
            });
        }

        order.status = 'Cancelled';
        appendStatusHistory(order, 'Cancelled', 'Cancelled by customer');
        await order.save();

        // STOCK STATE MACHINE:
        // Order PENDING   → Stock_Reserved++, Stock_Quantity--  (reserved on order create)
        // Order CONFIRMED → Stock_Reserved--,  Stock_Quantity unchanged  (stock already deducted)
        // Order CANCELLED → Stock_Reserved-- (release), Stock_Quantity++  (return to available)

        // Release reserved stock for each item
        await Promise.all(
            order.items.map(async (item) => {
                const product = await Product.findByIdAndUpdate(item.product, {
                    $inc: {
                        Stock_Quantity: item.quantity,
                        Stock_Reserved: -item.quantity
                    }
                }, { new: true });

                if (product) {
                    await StockHistory.create({
                        product: product._id,
                        action: 'Release',
                        change_type: 'Increase',
                        source_id: order._id.toString(),
                        balance_before: product.Stock_Quantity - item.quantity,
                        balance_after: product.Stock_Quantity,
                        changeAmount: item.quantity,
                        reason: 'Order Cancelled by User (Stock Returned)',
                        performedBy: userId
                    });
                }
            })
        );

        // Guard: clamp Stock_Reserved to 0 — prevents negative drift from race conditions
        await Product.updateMany(
            { _id: { $in: order.items.map(i => i.product) }, Stock_Reserved: { $lt: 0 } },
            { $set: { Stock_Reserved: 0 } }
        );

        res.json({ message: "Đã hủy đơn hàng thành công", order });

    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};
// 6. THEO DÕI ĐƠN HÀNG (Tracking Timeline)
exports.getOrderTracking = async (req, res) => {
    try {
        const order = await Order.findById(req.params.id).select('status createdAt updatedAt');

        if (!order) {
            return res.status(404).json({ message: "Không tìm thấy đơn hàng" });
        }

        // Giả lập Timeline dựa trên trạng thái
        const timeline = [
            { status: "Đã đặt hàng", time: order.createdAt, done: true },
            { status: "Đang xử lý", time: null, done: order.status === 'Processing' || order.status === 'Shipped' || order.status === 'Delivered' },
            { status: "Đã giao cho ĐVVC", time: null, done: order.status === 'Shipped' || order.status === 'Delivered' },
            { status: "Giao hàng thành công", time: order.status === 'Delivered' ? order.updatedAt : null, done: order.status === 'Delivered' }
        ];

        // Nếu đã hủy thì timeline khác
        if (order.status === 'Cancelled') {
            timeline.push({ status: "Đã hủy", time: order.updatedAt, done: true });
        }

        res.json({
            orderId: order._id,
            currentStatus: order.status,
            timeline: timeline
        });

    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};
// 7. ĐẶT LẠI (Reorder) -> Thêm items từ đơn cũ vào Cart
exports.reorder = async (req, res) => {
    try {
        const userId = req.user.id;
        const orderId = req.params.id;

        // 1. Lấy đơn hàng cũ
        const oldOrder = await Order.findById(orderId);
        if (!oldOrder) {
            return res.status(404).json({ message: "Đơn hàng cũ không tồn tại" });
        }

        // 2. Tìm hoặc Tạo Giỏ Hàng mới
        let cart = await Cart.findOne({ user: userId });
        if (!cart) {
            cart = new Cart({ user: userId, items: [], totalPrice: 0 });
        }

        // 3. Đẩy items cũ vào giỏ (Merge logic)
        for (const item of oldOrder.items) {
            // Kiểm tra xem sản phẩm còn bán không (Optional)

            const existingItemIndex = cart.items.findIndex(
                p => p.product.toString() === item.product.toString() && p.shade === item.shade
            );

            if (existingItemIndex > -1) {
                // Nếu đã có trong giỏ -> Cộng thêm số lượng
                cart.items[existingItemIndex].quantity += item.quantity;
            } else {
                // Nếu chưa có -> Thêm mới
                cart.items.push({
                    product: item.product,
                    quantity: item.quantity,
                    shade: item.shade
                });
            }
        }

        // 4. Tính lại tổng tiền (Quan trọng: Dùng giá hiện tại từ Product DB)
        let total = 0;
        for (const item of cart.items) {
            const prod = await Product.findById(item.product);
            if (prod) {
                total += prod.Price * item.quantity;
            }
        }
        cart.totalPrice = total;

        await cart.save();

        res.json({ message: "Đã thêm sản phẩm vào giỏ hàng!", cartSize: cart.items.length });

    } catch (error) {
        console.error("Reorder Error:", error);
        res.status(500).json({ message: "Lỗi server khi đặt lại đơn" });
    }
};

// 8. LẤY DỮ LIỆU ĐỂ ĐẶT LẠI (Reorder Data Checklist)
exports.getReorderData = async (req, res) => {
    try {
        const order = await Order.findById(req.params.id)
            .populate('items.product', 'Name Price Stock_Quantity Product_ID Thumbnail_Images');
        
        if (!order) return res.status(404).json({ message: 'Không tìm thấy đơn hàng' });
        
        // Security check
        if (order.user.toString() !== req.user.id && req.user.role !== 'admin') {
            return res.status(403).json({ message: 'Bạn không có quyền thực hiện hành động này' });
        }

        const Shade = require('../models/shade.model');
        const reorderItems = [];
        
        for (const item of order.items) {
            const product = item.product;
            let shadeStock = 999; 
            let currentPrice = product?.Price || item.price;
            let isAvailable = !!product && product.Stock_Quantity > 0;
            
            if (item.shade && product) {
                const shadeDoc = await Shade.findOne({ 
                    Product_ID: product.Product_ID, 
                    Shade_Name: item.shade 
                });
                if (shadeDoc) {
                    shadeStock = shadeDoc.Stock_Quantity;
                    if (shadeStock <= 0) isAvailable = false;
                } else {
                    isAvailable = false;
                }
            }
            
            reorderItems.push({
                productId: product?._id || item.product,
                productName: product?.Name || item.name,
                shade: item.shade,
                quantity: item.quantity,
                currentPrice: currentPrice,
                productStock: product?.Stock_Quantity || 0,
                shadeStock: shadeStock,
                isAvailable: isAvailable
            });
        }
        
        res.json({ items: reorderItems });
    } catch (err) {
        console.error('getReorderData Error:', err);
        res.status(500).json({ message: 'Lỗi server khi lấy dữ liệu đặt lại' });
    }
};