const { check } = require('express-validator');

exports.productValidator = [
    check('Name', 'Product name is required').not().isEmpty(),
    check('Price', 'Price must be a positive number').isFloat({ min: 0 }),
    check('Category', 'Category is required').not().isEmpty(),
    check('Stock_Quantity', 'Stock quantity must be a non-negative integer').isInt({ min: 0 })
];
