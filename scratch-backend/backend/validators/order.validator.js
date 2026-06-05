const { check, body } = require('express-validator');

/**
 * Validate order creation payload
 * - shippingAddress: fullName, phone, address, city are required
 * - paymentMethod must be one of the allowed values
 */
exports.createOrderValidator = [
    check('shippingAddress.fullName', 'Họ tên người nhận là bắt buộc').notEmpty().trim(),
    check('shippingAddress.phone', 'Số điện thoại là bắt buộc')
        .notEmpty()
        .matches(/^(0|\+84)[0-9]{9}$/)
        .withMessage('Số điện thoại không đúng định dạng'),
    check('shippingAddress.address', 'Địa chỉ cụ thể là bắt buộc').notEmpty().trim(),
    check('shippingAddress.city', 'Thành phố / tỉnh là bắt buộc').notEmpty().trim(),
    body('paymentMethod').notEmpty().withMessage('Payment method is required')
        .isIn(['VNPAY', 'MOMO', 'CARD']),
];

/**
 * Validate order status update (Admin only)
 */
exports.updateStatusValidator = [
    check('orderId', 'Order ID là bắt buộc').notEmpty(),
    check('status', 'Trạng thái không hợp lệ')
        .isIn(['Pending', 'Processing', 'Shipped', 'Delivered', 'Cancelled']),
];
