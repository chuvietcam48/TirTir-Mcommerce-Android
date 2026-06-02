const express = require('express');
const router = express.Router();
const { protect, authorize } = require('../middlewares/auth');
const userController = require('../controllers/admin.users.controller');

router.use(protect);
// router.use(authorize('admin')); // REMOVED global restriction

// Admin Management (Admin Only)
router.get('/admins', authorize('admin'), userController.getAdminUsers);
router.post('/admin', authorize('admin'), userController.createAdminUser);
router.put('/:id/role', authorize('admin'), userController.updateUserRole);
router.delete('/:id', authorize('admin'), userController.deleteUser);

// Customer Management (Admin + CSKH)
router.get('/', authorize('admin', 'customer_service'), userController.getAllUsers);
router.get('/:id', authorize('admin', 'customer_service'), userController.getUserDetail);
router.get('/:id/orders', authorize('admin', 'customer_service'), userController.getUserOrders);
router.put('/:id/status', authorize('admin', 'customer_service'), userController.updateUserStatus);

module.exports = router;
