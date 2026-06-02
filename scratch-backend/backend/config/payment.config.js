module.exports = {
  vnpay: {
    tmnCode: process.env.VNP_TMN_CODE || "YOUR_TMN_CODE",
    hashSecret: process.env.VNP_HASH_SECRET || "YOUR_HASH_SECRET",
    url: "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
    api: "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
    returnUrl: "http://localhost:5001/api/v1/payments/vnpay-return", // Endpoint backend xử lý return
  },
  momo: {
    partnerCode: process.env.MOMO_PARTNER_CODE || "MOMO",
    accessKey: process.env.MOMO_ACCESS_KEY || "F8BBA842ECF85", // Key Test mặc định của MoMo
    secretKey: process.env.MOMO_SECRET_KEY || "K951B6PE1waDMi640xX08PD3vg6EkVlz", // Key Test mặc định
    endpoint: "https://test-payment.momo.vn/v2/gateway/api/create",
    returnUrl: "http://localhost:5001/api/v1/payments/momo-return",
    ipnUrl: "http://localhost:5001/api/v1/payments/momo-ipn" // Dùng ngrok nếu test IPN local
  }
};