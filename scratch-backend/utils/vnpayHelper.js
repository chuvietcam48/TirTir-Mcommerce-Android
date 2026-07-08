// Force nodemon restart to pick up .env changes
const crypto = require('crypto');
const qs = require('querystring');

const VNP_TMN_CODE    = process.env.VNP_TMN_CODE    || 'DEMOTMN';
const VNP_HASH_SECRET = process.env.VNP_HASH_SECRET  || 'DEMOSECRET';
const VNP_URL         = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html';
// IPN backend endpoint — replace with your deployed URL
const VNP_RETURN_URL  = process.env.VNP_RETURN_URL   || 'https://your-backend.onrender.com/api/v1/payments/vnpay-return';

/**
 * Build a signed VNPAY payment URL.
 * @param {string} orderId   MongoDB Order._id
 * @param {number} amount    Final total in VND (integer, e.g. 250000)
 * @param {string} ipAddr    Caller IP address
 * @param {string} orderInfo Human-readable description
 * @returns {string}         Redirect URL for Android WebView / Intent
 */
function buildVnpayUrl(orderId, amount, ipAddr, orderInfo) {
  const now   = new Date();
  const pad   = (n) => String(n).padStart(2, '0');
  const date  = `${now.getFullYear()}${pad(now.getMonth()+1)}${pad(now.getDate())}` +
                `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const expire = (() => {
    const d = new Date(now.getTime() + 15 * 60 * 1000);
    return `${d.getFullYear()}${pad(d.getMonth()+1)}${pad(d.getDate())}` +
           `${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
  })();

  const params = {
    vnp_Version:     '2.1.0',
    vnp_Command:     'pay',
    vnp_TmnCode:     VNP_TMN_CODE,
    vnp_Amount:      Math.round(amount) * 100,  // VNPay expects amount × 100
    vnp_CurrCode:    'VND',
    vnp_TxnRef:      String(orderId),
    vnp_OrderInfo:   orderInfo,
    vnp_OrderType:   'other',
    vnp_Locale:      'vn',
    vnp_ReturnUrl:   VNP_RETURN_URL,
    vnp_IpAddr:      ipAddr || '127.0.0.1',
    vnp_CreateDate:  date,
    vnp_ExpireDate:  expire,
  };

  const sortedKeys = Object.keys(params).sort();
  const signDataArr = [];
  for (const k of sortedKeys) {
    signDataArr.push(`${k}=${encodeURIComponent(String(params[k])).replace(/%20/g, "+")}`);
  }
  const signData = signDataArr.join('&');
  const signature = crypto.createHmac('sha512', VNP_HASH_SECRET)
                          .update(Buffer.from(signData, 'utf-8'))
                          .digest('hex');

  return `${VNP_URL}?${signData}&vnp_SecureHash=${signature}`;
}

/**
 * Verify a VNPAY IPN / return-URL callback.
 * @param {object} query   req.query or req.body from VNPAY
 * @returns {{ valid: boolean, success: boolean, txnRef: string }}
 */
function verifyVnpayCallback(query) {
  const secureHash = query.vnp_SecureHash;
  const params = Object.assign({}, query);
  delete params.vnp_SecureHash;
  delete params.vnp_SecureHashType;

  const sortedKeys = Object.keys(params).sort();
  const signDataArr = [];
  for (const k of sortedKeys) {
    signDataArr.push(`${k}=${encodeURIComponent(String(params[k])).replace(/%20/g, "+")}`);
  }
  const signData = signDataArr.join('&');
  const expected  = crypto.createHmac('sha512', VNP_HASH_SECRET)
                          .update(Buffer.from(signData, 'utf-8'))
                          .digest('hex');

  const valid   = expected === secureHash;
  const success = query.vnp_ResponseCode === '00';
  const txnRef  = query.vnp_TxnRef;
  return { valid, success, txnRef };
}

module.exports = { buildVnpayUrl, verifyVnpayCallback };
