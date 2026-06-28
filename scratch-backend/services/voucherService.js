const admin = require('firebase-admin');
const Voucher = require('../models/Voucher');

/**
 * Grant a voucher to a user
 * Inserts voucher into users/{uid}/vouchers in Firestore and Voucher collection in MongoDB
 */
async function grantVoucher(userId, discountPct = 5, description = 'Special Reward', expiryDays = 30) {
  if (!userId) throw new Error('userId is required');

  const randomSuffix = Math.random().toString(36).substring(2, 7).toUpperCase();
  const voucherCode = `TIRTIR-${discountPct}OFF-${randomSuffix}`;
  const expiryDate = new Date(Date.now() + expiryDays * 24 * 60 * 60 * 1000);

  // 1. Save to MongoDB
  const mongoVoucher = await Voucher.create({
    voucherCode,
    userId,
    discountPct,
    description,
    expiryDate,
    isUsed: false
  });

  // 2. Save to Firestore users/{uid}/vouchers
  try {
    const db = admin.firestore();
    const firestoreVoucherData = {
      voucherCode,
      code: voucherCode,
      discountPct,
      description,
      expiryDate: expiryDate.toISOString(),
      expiresAt: expiryDate.toISOString(),
      active: true,
      isUsed: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    };

    await db.collection('users')
      .doc(String(userId))
      .collection('vouchers')
      .doc(voucherCode)
      .set(firestoreVoucherData);
  } catch (fsErr) {
    console.error('[VOUCHER_SERVICE] Firestore voucher sync error:', fsErr.message);
  }

  return {
    voucherCode,
    discountPct,
    expiryDate: expiryDate.toISOString()
  };
}

module.exports = {
  grantVoucher
};
