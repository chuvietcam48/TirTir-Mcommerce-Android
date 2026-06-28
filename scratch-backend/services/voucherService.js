const crypto = require('crypto');
const admin = require('firebase-admin');
const Voucher = require('../models/Voucher');

/**
 * Helper to generate unique code in format TIRTIR-XXXXXX
 */
function generateVoucherCode() {
  const prefix = process.env.VOUCHER_PREFIX || 'TIRTIR';
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let randomStr = '';
  for (let i = 0; i < 6; i++) {
    randomStr += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return `${prefix}-${randomStr}`;
}

/**
 * Grant a voucher to a user
 * Supports both object options and positional parameters for backward compatibility.
 */
async function grantVoucher(userId, optionsOrDiscount = 5, descriptionOrReason = 'PUBLIC_ROUTINE_REWARD', expiryDaysArg = 30) {
  if (!userId) throw new Error('userId is required');

  let discountPct = 5;
  let reason = 'PUBLIC_ROUTINE_REWARD';
  let source = 'routine';
  let expiryDays = 30;
  let description = 'Discount Voucher';

  if (typeof optionsOrDiscount === 'object' && optionsOrDiscount !== null) {
    discountPct = optionsOrDiscount.discountPct !== undefined ? optionsOrDiscount.discountPct : 5;
    reason = optionsOrDiscount.reason || 'PUBLIC_ROUTINE_REWARD';
    source = optionsOrDiscount.source || 'routine';
    expiryDays = optionsOrDiscount.expiryDays !== undefined ? optionsOrDiscount.expiryDays : 30;
    description = optionsOrDiscount.description || optionsOrDiscount.reason || 'Discount Voucher';
  } else {
    discountPct = typeof optionsOrDiscount === 'number' ? optionsOrDiscount : 5;
    if (typeof descriptionOrReason === 'string') {
      reason = descriptionOrReason;
      description = descriptionOrReason;
    }
    if (typeof expiryDaysArg === 'number') {
      expiryDays = expiryDaysArg;
    }
  }

  const envDiscount = process.env.ROUTINE_REWARD_DISCOUNT ? parseInt(process.env.ROUTINE_REWARD_DISCOUNT, 10) : null;
  if (envDiscount && !isNaN(envDiscount) && discountPct === 5) {
    discountPct = envDiscount;
  }

  const envExpiry = process.env.ROUTINE_REWARD_EXPIRY_DAYS ? parseInt(process.env.ROUTINE_REWARD_EXPIRY_DAYS, 10) : null;
  if (envExpiry && !isNaN(envExpiry) && expiryDays === 30) {
    expiryDays = envExpiry;
  }

  const code = generateVoucherCode();
  const now = new Date();
  const expiryDate = new Date(now.getTime() + expiryDays * 24 * 60 * 60 * 1000);

  // 1. Store in MongoDB vouchers collection
  const mongoVoucher = await Voucher.create({
    code,
    voucherCode: code,
    userId,
    discountPct,
    reason,
    source,
    status: 'active',
    description,
    expiryDate,
    isUsed: false,
    usedAt: null
  });

  // 2. Store in Firestore users/{userId}/vouchers/{voucherId}
  try {
    const db = admin.firestore();
    const firestoreVoucherData = {
      code,
      voucherCode: code,
      discountPct,
      reason,
      source,
      status: 'active',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      expiryDate: expiryDate.toISOString(),
      usedAt: null
    };

    await db.collection('users')
      .doc(String(userId))
      .collection('vouchers')
      .doc(code)
      .set(firestoreVoucherData);
  } catch (fsErr) {
    console.error('[VOUCHER_SERVICE] Firestore voucher sync error:', fsErr.message);
  }

  return {
    code,
    voucherCode: code,
    discountPct,
    reason,
    source,
    status: 'active',
    createdAt: now.toISOString(),
    expiryDate: expiryDate.toISOString(),
    usedAt: null
  };
}

module.exports = {
  grantVoucher
};

