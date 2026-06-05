const express = require('express');
const router = express.Router();
const multer = require('multer');
const { protect, authorize } = require('../middlewares/auth');
const smartController = require('../controllers/admin.smart.controller');

// In-memory upload for sharp processing
const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 5 * 1024 * 1024 } // 5MB limit
});

router.get('/suggest-parent', protect, authorize('admin', 'editor'), smartController.suggestParent);
router.post('/generate-ids', protect, authorize('admin', 'editor'), smartController.generateIds);
router.post('/extract-color', protect, authorize('admin', 'editor'), upload.single('image'), smartController.extractColor);
router.post('/extract-color/hex', protect, authorize('admin', 'editor'), smartController.extractColorHex);

module.exports = router;
