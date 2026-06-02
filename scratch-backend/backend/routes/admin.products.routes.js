const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const { protect, authorize } = require('../middlewares/auth');
const { validate } = require('../middlewares/validate');
const { productValidator } = require('../validators/product.validator');
const productController = require('../controllers/product.controller');
const { productFieldsUploader } = require('../middlewares/upload.middleware');

// In-memory CSV uploader (no disk write, buffer available at req.file.buffer)
const csvUpload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 5 * 1024 * 1024 }, // 5MB
    fileFilter: (req, file, cb) => {
        const ext = path.extname(file.originalname).toLowerCase();
        if (ext === '.csv' || file.mimetype === 'text/csv' || file.mimetype === 'application/vnd.ms-excel') {
            cb(null, true);
        } else {
            cb(new Error('Only CSV files are allowed for bulk import'), false);
        }
    }
});

router.post('/', protect, authorize('admin'), productFieldsUploader, productValidator, validate, productController.createProduct);
router.post('/bulk-import', protect, authorize('admin'), csvUpload.single('file'), productController.bulkImport);
router.put('/:id', protect, authorize('admin'), productFieldsUploader, productValidator, validate, productController.updateProduct);
router.delete('/:id', protect, authorize('admin'), productController.deleteProduct);
router.patch('/:id/stock', protect, authorize('admin'), productController.updateStock);

module.exports = router;

