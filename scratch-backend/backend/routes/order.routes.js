const express = require('express');
const router = express.Router();
const orderController = require('../controllers/order.controller');
const { protect, authorize } = require('../middlewares/auth');
const { createOrderValidator, updateStatusValidator } = require('../validators/order.validator');
const { validate } = require('../middlewares/validate');
const { checkoutLimiter } = require('../middlewares/rateLimit');


/**
 * @swagger
 * tags:
 *   name: Orders
 *   description: Quản lý đơn hàng cho user và admin
 */

/**
 * @swagger
 * /orders/create:
 *   post:
 *     summary: Tạo đơn hàng mới từ giỏ hàng
 *     tags: [Orders]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [shippingAddress, paymentMethod]
 *             properties:
 *               shippingAddress:
 *                 type: object
 *                 properties:
 *                   fullName: { type: string, example: "Nguyen Van A" }
 *                   phone: { type: string, example: "0912345678" }
 *                   address: { type: string, example: "123 Le Loi" }
 *                   city: { type: string, example: "Ho Chi Minh" }
 *               paymentMethod:
 *                 type: string
 *                 enum: [VNPAY, MOMO, CARD]
 *                 example: VNPAY
 *     responses:
 *       201:
 *         description: Đặt hàng thành công
 *       400:
 *         description: Giỏ hàng trống hoặc hết hàng
 *       401:
 *         description: Chưa đăng nhập
 */
router.post('/create', protect, checkoutLimiter, createOrderValidator, validate, orderController.createOrder);

/**
 * @swagger
 * /orders/my-orders:
 *   get:
 *     summary: Lấy lịch sử đơn hàng của user hiện tại
 *     tags: [Orders]
 *     responses:
 *       200:
 *         description: Danh sách đơn hàng
 */
router.get('/my-orders', protect, orderController.getMyOrders);

/**
 * @swagger
 * /orders/update-status:
 *   put:
 *     summary: Cập nhật trạng thái đơn hàng (Admin/Staff only)
 *     tags: [Orders]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [orderId, status]
 *             properties:
 *               orderId: { type: string, example: "64abc123..." }
 *               status:
 *                 type: string
 *                 enum: [Pending, Processing, Shipped, Delivered, Cancelled]
 *     responses:
 *       200:
 *         description: Cập nhật thành công
 *       400:
 *         description: Trạng thái không hợp lệ
 *       403:
 *         description: Không có quyền
 *       404:
 *         description: Không tìm thấy đơn hàng
 */
router.put('/update-status', protect, authorize('admin', 'customer_service', 'inventory_staff'), updateStatusValidator, validate, orderController.updateOrderStatus);


/**
 * @swagger
 * /orders/{id}/tracking:
 *   get:
 *     summary: Theo dõi trạng thái đơn hàng (timeline)
 *     tags: [Orders]
 *     security: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: string }
 *         description: Order ID
 *     responses:
 *       200:
 *         description: Timeline trạng thái đơn hàng
 *       404:
 *         description: Không tìm thấy đơn hàng
 */
router.get('/:id/tracking', orderController.getOrderTracking);

/**
 * @swagger
 * /orders/{id}/cancel:
 *   post:
 *     summary: Hủy đơn hàng (chỉ khi status = Pending)
 *     tags: [Orders]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: string }
 *     responses:
 *       200:
 *         description: Đã hủy đơn hàng thành công
 *       400:
 *         description: Không thể hủy đơn đã xử lý
 *       404:
 *         description: Không tìm thấy đơn hàng
 */
router.post('/:id/cancel', protect, orderController.cancelOrder);

/**
 * @swagger
 * /orders/{id}/reorder:
 *   post:
 *     summary: Đặt lại đơn hàng cũ (thêm vào giỏ hàng)
 *     tags: [Orders]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: string }
 *     responses:
 *       200:
 *         description: Đã thêm sản phẩm vào giỏ hàng
 *       404:
 *         description: Không tìm thấy đơn hàng cũ
 */
router.post('/:id/reorder', protect, orderController.reorder);

/**
 * @swagger
 * /orders/{id}/reorder-data:
 *   get:
 *     summary: Lấy dữ liệu kiểm tra kho/giá để đặt lại đơn hàng
 *     tags: [Orders]
 */
router.get('/:id/reorder-data', protect, orderController.getReorderData);

/**
 * @swagger
 * /orders/{id}:
 *   get:
 *     summary: Xem chi tiết đơn hàng
 *     tags: [Orders]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: string }
 *     responses:
 *       200:
 *         description: Chi tiết đơn hàng
 *       403:
 *         description: Không có quyền xem đơn hàng này
 *       404:
 *         description: Không tìm thấy đơn hàng
 */
router.get('/:id', protect, orderController.getOrderById);

/**
 * @swagger
 * /orders/{id}/fulfillment:
 *   put:
 *     summary: Cập nhật thông tin fulfillment (carrier, trackingNumber, isPacked) — Admin only
 *     tags: [Orders]
 */
router.put('/:id/fulfillment', protect, authorize('admin', 'customer_service', 'inventory_staff'), orderController.updateFulfillment);

module.exports = router;
