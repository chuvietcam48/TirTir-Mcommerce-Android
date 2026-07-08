const express = require('express');
const router = express.Router();
const {
  getProfile,
  updateProfile,
  getAddresses,
  addAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress,
  updateSkinProfile,
} = require('../controllers/userController');
const { protect } = require('../middleware/authMiddleware');

// All user routes require authentication
router.use(protect);

// ── Profile ──────────────────────────────
// GET  /api/v1/users/profile       → ApiService.getProfile()
// PUT  /api/v1/users/profile       → ApiService.updateProfile()
router.get('/profile', getProfile);
router.put('/profile', updateProfile);
router.put('/skin-profile', updateSkinProfile);

// ── Addresses ────────────────────────────
// GET    /api/v1/users/addresses          → ApiService.getAddresses()
// POST   /api/v1/users/addresses          → ApiService.addAddress()
// PUT    /api/v1/users/addresses/:id      → ApiService.updateAddress()
// DELETE /api/v1/users/addresses/:id      → ApiService.deleteAddress()
// PATCH  /api/v1/users/addresses/:id/set-default → ApiService.setDefaultAddress()
router.get('/addresses', getAddresses);
router.post('/addresses', addAddress);
router.put('/addresses/:id', updateAddress);
router.delete('/addresses/:id', deleteAddress);
router.patch('/addresses/:id/set-default', setDefaultAddress);

module.exports = router;
