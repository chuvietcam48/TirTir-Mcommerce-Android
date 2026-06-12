const express = require('express');
const router = express.Router();
const multer = require('multer');
const { uploadImage } = require('../controllers/uploadController');
const { protect } = require('../middleware/authMiddleware');

// Configure multer to store file in memory
const upload = multer({ 
    storage: multer.memoryStorage(),
    limits: { fileSize: 5 * 1024 * 1024 } // 5MB limit
});

// POST /api/v1/upload
// Note: We use `protect` to ensure only logged-in users can upload images
router.post('/', protect, upload.single('image'), uploadImage);

module.exports = router;
