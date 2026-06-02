const { check } = require('express-validator');

/**
 * Validate profile update payload
 */
exports.updateProfileValidator = [
    check('name', 'Tên không được để trống').optional().notEmpty().trim(),
    check('phone', 'Số điện thoại không đúng định dạng')
        .optional()
        .matches(/^(0|\+84)[0-9]{9}$/)
        .withMessage('Số điện thoại phải có 10 chữ số'),
    check('gender', 'Giới tính không hợp lệ')
        .optional()
        .isIn(['Male', 'Female', 'Other']),
    check('birthDate', 'Ngày sinh không đúng định dạng')
        .optional()
        .isISO8601()
        .withMessage('Ngày sinh phải theo định dạng YYYY-MM-DD'),
];

/**
 * Validate change password payload
 */
exports.changePasswordValidator = [
    check('currentPassword', 'Mật khẩu hiện tại là bắt buộc').notEmpty(),
    check('newPassword', 'Mật khẩu mới phải có ít nhất 6 ký tự').isLength({ min: 6 }),
];

/**
 * Validate add/update address payload
 */
exports.addressValidator = [
    check('fullName', 'Họ tên là bắt buộc').notEmpty().trim(),
    check('phone', 'Số điện thoại là bắt buộc')
        .notEmpty()
        .matches(/^(0|\+84)[0-9]{9}$/)
        .withMessage('Số điện thoại không đúng định dạng'),
    check('street', 'Địa chỉ cụ thể là bắt buộc').notEmpty().trim(),
    check('city', 'Thành phố là bắt buộc').notEmpty().trim(),
    check('district', 'Quận/huyện là bắt buộc').notEmpty().trim(),
    check('ward', 'Phường/xã là bắt buộc').notEmpty().trim(),
];
