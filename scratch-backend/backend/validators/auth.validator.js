const { check } = require('express-validator');

exports.registerValidator = [
    check('firstName', 'First name is required').trim().notEmpty(),
    check('lastName',  'Last name is required').trim().notEmpty(),
    check('email',    'Please include a valid email').isEmail().normalizeEmail(),
    check('password', 'Password must be at least 8 characters').isLength({ min: 8 })
];

exports.loginValidator = [
    check('email', 'Please include a valid email').isEmail(),
    check('password', 'Password is required').exists()
];
