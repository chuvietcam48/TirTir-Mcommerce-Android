const Voucher = require('../models/Voucher');

// GET /api/v1/vouchers/my-vouchers or /api/v1/users/vouchers
exports.getUserVouchers = async (req, res) => {
  try {
    const userId = req.user.id;
    const vouchers = await Voucher.find({
      userId,
      isUsed: false,
      expiryDate: { $gte: new Date() }
    }).sort({ createdAt: -1 }).lean();

    res.status(200).json({ success: true, count: vouchers.length, data: vouchers });
  } catch (err) {
    console.error('Error fetching vouchers:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// POST /api/v1/vouchers/validate
exports.validateVoucher = async (req, res) => {
  try {
    const { voucherCode } = req.body;
    const userId = req.user.id;

    if (!voucherCode) return res.status(400).json({ success: false, message: 'voucherCode is required' });

    const voucher = await Voucher.findOne({
      voucherCode: voucherCode.toUpperCase(),
      userId,
      isUsed: false,
      expiryDate: { $gte: new Date() }
    });

    if (!voucher) {
      return res.status(404).json({ success: false, message: 'Mã voucher không hợp lệ hoặc đã hết hạn.' });
    }

    res.status(200).json({
      success: true,
      data: {
        voucherCode: voucher.voucherCode,
        discountPct: voucher.discountPct,
        description: voucher.description
      }
    });
  } catch (err) {
    console.error('Error validating voucher:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};
