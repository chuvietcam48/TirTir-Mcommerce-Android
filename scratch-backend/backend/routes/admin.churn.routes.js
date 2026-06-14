const express = require('express');
const router = express.Router();
const { getChurnList, sendManualVoucher } = require('../controllers/admin.churn.controller');
const { protect, authorize } = require('../middlewares/auth');

router.use(protect);
router.use(authorize('admin')); // Restrict all churn endpoints to admins

router.get('/', getChurnList);
router.post('/send-voucher', sendManualVoucher);

module.exports = router;
