const express = require('express');
const router = express.Router();
const { getWishlist, syncWishlist } = require('../controllers/wishlistController');
const { protect } = require('../middleware/authMiddleware');

router.get('/', protect, getWishlist);
router.post('/sync', protect, syncWishlist);

module.exports = router;
