const axios = require('axios');
const moment = require('moment');
const querystring = require('qs');
const crypto = require('crypto');
const paymentConfig = require('../config/payment.config');
const Order = require('../models/order.model');
const Product = require('../models/product.model')
const EXCHANGE_RATE = 25000;

// Hàm sort object bắt buộc của VNPay để tạo chữ ký đúng
function sortObject(obj) {
    let sorted = {};
    let str = [];
    let key;
    for (key in obj) {
        if (obj.hasOwnProperty(key)) {
            str.push(encodeURIComponent(key));
        }
    }
    str.sort();
    for (key = 0; key < str.length; key++) {
        sorted[str[key]] = encodeURIComponent(obj[str[key]]).replace(/%20/g, "+");
    }
    return sorted;
}

exports.createPaymentUrl = async (req, res, next) => {
    try {
        const { orderId, paymentMethod, bankCode, language = 'vn' } = req.body;

        if (!orderId) {
            return res.status(400).json({ message: "Missing orderId" });
        }

        const order = await Order.findById(orderId);
        if (!order) {
            return res.status(404).json({ message: "Order not found" });
        }

        if (order.paymentStatus === 'PAID') {
            return res.status(400).json({ message: "Order is already paid" });
        }

        // Single Source of Truth
        const amount = order.totalAmount;

        let paymentUrl = '';

        switch (paymentMethod) {
            case 'VNPAY':
                paymentUrl = createVNPayUrl(req, orderId, amount, bankCode, language);
                break;
            case 'CARD':
                paymentUrl = createVNPayUrl(req, orderId, amount, 'INTCARD', language);
                break;
            case 'MOMO':
                paymentUrl = await createMomoUrl(orderId, amount);
                break;
            default:
                return res.status(400).json({ message: "Invalid payment method" });
        }

        res.status(200).json({ paymentUrl });

    } catch (error) {
        console.error("Payment Controller Error:", error);
        res.status(500).json({
            message: error.message || "Lỗi tạo thanh toán",
            detail: error.response?.data || "No details"
        });
    }
};

// --- LOGIC VNPAY ---
function createVNPayUrl(req, orderId, amount, bankCode, language) {
    const amountInVND = Math.round(amount * EXCHANGE_RATE);
    process.env.TZ = 'Asia/Ho_Chi_Minh';
    let date = new Date();
    let createDate = moment(date).format('YYYYMMDDHHmmss');

    let ipAddr = req.headers['x-forwarded-for'] ||
        req.connection.remoteAddress ||
        req.socket.remoteAddress ||
        req.connection.socket.remoteAddress;

    let tmnCode = paymentConfig.vnpay.tmnCode;
    let secretKey = paymentConfig.vnpay.hashSecret;
    let vnpUrl = paymentConfig.vnpay.url;
    let returnUrl = paymentConfig.vnpay.returnUrl;

    let vnp_Params = {};
    vnp_Params['vnp_Version'] = '2.1.0';
    vnp_Params['vnp_Command'] = 'pay';
    vnp_Params['vnp_TmnCode'] = tmnCode;
    vnp_Params['vnp_Locale'] = language;
    vnp_Params['vnp_CurrCode'] = 'VND';
    vnp_Params['vnp_TxnRef'] = orderId; // Mã đơn hàng
    vnp_Params['vnp_OrderInfo'] = 'Thanh toan cho ma GD:' + orderId;
    vnp_Params['vnp_OrderType'] = 'other';
    vnp_Params['vnp_Amount'] = amountInVND * 100; // VNPay tính đơn vị đồng (x100)
    vnp_Params['vnp_ReturnUrl'] = returnUrl;
    vnp_Params['vnp_IpAddr'] = ipAddr;
    vnp_Params['vnp_CreateDate'] = createDate;

    if (bankCode) {
        vnp_Params['vnp_BankCode'] = bankCode;
    }

    vnp_Params = sortObject(vnp_Params);

    let signData = querystring.stringify(vnp_Params, { encode: false });
    let hmac = crypto.createHmac("sha512", secretKey);
    let signed = hmac.update(new Buffer.from(signData, 'utf-8')).digest("hex");
    vnp_Params['vnp_SecureHash'] = signed;

    vnpUrl += '?' + querystring.stringify(vnp_Params, { encode: false });
    return vnpUrl;
}

// GET /api/payments/vnpay-return
exports.vnpayReturn = async (req, res, next) => {
    let vnp_Params = req.query;
    let secureHash = vnp_Params['vnp_SecureHash'];

    delete vnp_Params['vnp_SecureHash'];
    delete vnp_Params['vnp_SecureHashType'];

    vnp_Params = sortObject(vnp_Params);

    let tmnCode = paymentConfig.vnpay.tmnCode;
    let secretKey = paymentConfig.vnpay.hashSecret;

    let signData = querystring.stringify(vnp_Params, { encode: false });
    let hmac = crypto.createHmac("sha512", secretKey);
    let signed = hmac.update(new Buffer.from(signData, 'utf-8')).digest("hex");

    if (secureHash === signed) {
        // Kiem tra xem du lieu trong db co hop le hay khong va thong bao ket qua
        const orderId = vnp_Params['vnp_TxnRef'];
        const rspCode = vnp_Params['vnp_ResponseCode'];
        const vnp_TransactionNo = vnp_Params['vnp_TransactionNo']; //mã giao dịch

        // Redirect về Frontend (Angular)
        if (rspCode === '00') {
            // Cập nhật trạng thái đơn hàng = PAID tại đây hoặc trong IPN
            await Order.findByIdAndUpdate(orderId, {
                paymentStatus: 'PAID',
                paymentMethod: 'VNPAY',
                paymentTranId: vnp_TransactionNo
            });

            // Redirect về trang Success của Frontend
            res.redirect(`http://localhost:4200/order-confirmation/${orderId}?status=success`);
        } else {
            // Redirect về trang Failed/Checkout của Frontend
            res.redirect(`http://localhost:4200/checkout?status=failed&orderId=${orderId}`);
        }
    } else {
        res.redirect(`http://localhost:4200/checkout?status=error_signature`);
    }
};

// POST /api/payments/vnpay-ipn
exports.vnpayIPN = async (req, res, next) => {
    let vnp_Params = req.query;
    let secureHash = vnp_Params['vnp_SecureHash'];

    let orderId = vnp_Params['vnp_TxnRef'];
    let rspCode = vnp_Params['vnp_ResponseCode'];
    let vnp_TransactionNo = vnp_Params['vnp_TransactionNo'];

    delete vnp_Params['vnp_SecureHash'];
    delete vnp_Params['vnp_SecureHashType'];

    vnp_Params = sortObject(vnp_Params);
    let secretKey = paymentConfig.vnpay.hashSecret;
    let signData = querystring.stringify(vnp_Params, { encode: false });
    let hmac = crypto.createHmac("sha512", secretKey);
    let signed = hmac.update(new Buffer.from(signData, 'utf-8')).digest("hex");

    if (secureHash === signed) {
        // Tìm đơn hàng trong DB
        const order = await Order.findById(orderId);
        if (!order) {
            return res.status(200).json({ RspCode: '01', Message: 'Order not found' });
        }

        // Kiểm tra số tiền (quan trọng)
        // if (order.totalAmount * 100 !== parseInt(vnp_Params['vnp_Amount'])) ...

        // Kiểm tra trạng thái đơn hàng hiện tại (chống update chồng chéo)
        if (order.paymentStatus !== 'PAID') {
            if (rspCode === '00') {
                // Thành công
                order.paymentStatus = 'PAID';
                order.paymentTranId = vnp_TransactionNo;
                order.paymentInfo = vnp_Params; // Lưu lại log VNPay
                await order.save();
                res.status(200).json({ RspCode: '00', Message: 'Success' });
            } else {
                // Thất bại
                // order.paymentStatus = 'failed';
                // await order.save();
                res.status(200).json({ RspCode: '00', Message: 'Success' });
            }
        } else {
            res.status(200).json({ RspCode: '02', Message: 'This order has been updated to the payment status' });
        }
    } else {
        res.status(200).json({ RspCode: '97', Message: 'Checksum failed' });
    }
};

// GET /api/orders/:id/payment-status
exports.checkPaymentStatus = async (req, res, next) => {
    try {
        const order = await Order.findById(req.params.id);
        if (!order) return res.status(404).json({ message: "Order not found" });

        res.json({
            status: order.paymentStatus,
            method: order.paymentMethod
        });
    } catch (err) {
        next(err);
    }
};

async function createMomoUrl(orderId, amount) {
    // Config Test Cứng (Dùng luôn để test)
    const partnerCode = "MOMO";
    const accessKey = "F8BBA842ECF85";
    const secretKey = "K951B6PE1waDMi640xX08PD3vg6EkVlz";
    const endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";

    // Redirect về Frontend
    //const returnUrl = "http://localhost:4200/order-confirmation/" + orderId;
    const returnUrl = "http://localhost:5001/api/v1/payments/momo-return";
    const ipnUrl = "http://localhost:5001/api/v1/payments/momo-ipn"; // Localhost k nhận dc IPN thật nhưng kệ nó

    const requestId = partnerCode + new Date().getTime();
    const orderInfo = "Thanh toan don hang " + orderId;
    const requestType = "captureWallet";
    const extraData = "";

    // FIX QUAN TRỌNG: Chuyển amount sang VND + convert sang string
    const amountInVND = Math.round(amount * EXCHANGE_RATE);
    const amountStr = amountInVND.toString();

    // Tạo chữ ký
    const rawSignature = `accessKey=${accessKey}&amount=${amountStr}&extraData=${extraData}&ipnUrl=${ipnUrl}&orderId=${orderId}&orderInfo=${orderInfo}&partnerCode=${partnerCode}&redirectUrl=${returnUrl}&requestId=${requestId}&requestType=${requestType}`;

    const signature = crypto.createHmac('sha256', secretKey)
        .update(rawSignature)
        .digest('hex');

    // Body gửi sang MoMo
    const requestBody = {
        partnerCode,
        partnerName: "TirTir Store",
        storeId: "MomoTestStore",
        requestId,
        amount: amountStr, // Gửi string
        orderId,
        orderInfo,
        redirectUrl: returnUrl,
        ipnUrl,
        lang: 'vi',
        requestType,
        autoCapture: true,
        extraData,
        signature
    };

    // Gọi API
    const response = await axios.post(endpoint, requestBody);
    return response.data.payUrl;
}

// GET /api/payments/momo-return
exports.momoReturn = async (req, res, next) => {
    try {
        const { resultCode, orderId, transId } = req.query;
        // resultCode = 0 là thành công

        if (resultCode == '0') {
            // Cập nhật DB
            await Order.findByIdAndUpdate(orderId, {
                paymentStatus: 'PAID',
                paymentMethod: 'MOMO',
                paymentTranId: transId
            });
            res.redirect(`http://localhost:4200/order-confirmation/${orderId}?status=success`);
        } else {
            res.redirect(`http://localhost:4200/checkout?status=failed&orderId=${orderId}`);
        }
    } catch (error) {
        next(error);
    }
};

// POST /api/payments/momo-ipn
exports.momoIPN = async (req, res, next) => {
    // MoMo gọi vào đây để báo kết quả (Server-to-Server)
    try {
        const { resultCode, orderId, transId } = req.body; // MoMo gửi body JSON

        if (resultCode == '0') {
            await Order.findByIdAndUpdate(orderId, {
                paymentStatus: 'PAID',
                paymentTranId: transId,
                paymentInfo: req.body
            });
        }
        res.status(204).send(); // Trả về 204 No Content để MoMo biết đã nhận
    } catch (error) {
        console.error(error);
        res.status(500).send();
    }
};

exports.cancelAndRefundOrder = async (req, res) => {
    try {
        const { orderId } = req.params;
        const order = await Order.findById(orderId);

        if (!order) return res.status(404).json({ message: 'Đơn hàng không tồn tại' });
        if (order.orderStatus === 'CANCELLED') return res.status(400).json({ message: 'Đơn hàng đã được hủy trước đó' });
        if (['SHIPPING', 'DELIVERED'].includes(order.orderStatus)) {
            return res.status(400).json({ message: 'Không thể hủy đơn hàng đang giao hoặc đã giao' });
        }

        // TRƯỜNG HỢP 1: CHƯA THANH TOÁN (Chỉ cần hủy đơn)
        if (order.paymentStatus === 'PENDING' || order.paymentStatus === 'FAILED') {
            order.orderStatus = 'CANCELLED';
            await order.save();
            await restoreInventory(order.products); // Hàm tự viết để +1 lại vào kho
            return res.status(200).json({ message: 'Hủy đơn hàng thành công. Không cần hoàn tiền.' });
        }

        // TRƯỜNG HỢP 2: ĐÃ THANH TOÁN -> GỌI API REFUND
        if (order.paymentStatus === 'PAID') {
            let refundResult = false;

            if (order.paymentMethod === 'MOMO') {
                refundResult = await handleMomoRefund(order);
            } else if (order.paymentMethod === 'VNPAY') {
                refundResult = await handleVNPayRefund(order, req);
            }

            if (refundResult) {
                order.paymentStatus = 'REFUNDED';
                order.orderStatus = 'CANCELLED';
                await order.save();
                await restoreInventory(order.products);
                return res.status(200).json({ message: 'Hủy đơn và hoàn tiền thành công!' });
            } else {
                return res.status(500).json({ message: 'Lỗi khi kết nối với cổng thanh toán để hoàn tiền.' });
            }
        }
    } catch (error) {
        console.error('Cancel & Refund Error:', error);
        res.status(500).json({ message: 'Lỗi server' });
    }
};

// --- HÀM HỖ TRỢ REFUND MOMO ---
async function handleMomoRefund(order) {
    const { partnerCode, accessKey, secretKey } = paymentConfig.momo;
    const requestId = order._id + new Date().getTime();
    const orderId = requestId; // Trùng requestId cho giao dịch refund
    const transId = order.paymentTranId; // Lấy từ DB lúc IPN trả về thành công

    const rawSignature = `accessKey=${accessKey}&amount=${order.totalAmount}&description=Hoan tien don hang&orderId=${orderId}&partnerCode=${partnerCode}&requestId=${requestId}&transId=${transId}`;
    const signature = crypto.createHmac('sha256', secretKey).update(rawSignature).digest('hex');

    const requestBody = {
        partnerCode,
        requestId,
        orderId,
        amount: order.totalAmount,
        transId,
        lang: 'vi',
        description: 'Hoan tien don hang',
        signature
    };

    try {
        const response = await axios.post('https://test-payment.momo.vn/v2/gateway/api/refund', requestBody);
        return response.data.resultCode === 0; // 0 là thành công
    } catch (error) {
        console.error('Momo Refund API Error:', error.response?.data || error.message);
        return false;
    }
}

// --- HÀM HỖ TRỢ REFUND VNPAY ---
async function handleVNPayRefund(order, req) {
    // VNPay Refund yêu cầu gọi đến Endpoint vnpay_api
    const { vnp_TmnCode, vnp_HashSecret, vnp_ApiUrl } = paymentConfig.vnpay;
    const date = new Date();
    const createDate = moment(date).format('YYYYMMDDHHmmss');
    const ipAddr = req.headers['x-forwarded-for'] || req.connection.remoteAddress;

    const data = {
        vnp_RequestId: order._id + createDate,
        vnp_Version: '2.1.0',
        vnp_Command: 'refund',
        vnp_TmnCode: vnp_TmnCode,
        vnp_TransactionType: '02', // 02: Hoàn toàn phần, 03: Hoàn 1 phần
        vnp_TxnRef: order._id,
        vnp_Amount: order.totalAmount * 100,
        vnp_TransactionNo: order.paymentTranId,
        vnp_OrderInfo: 'Hoan tien don hang ' + order._id,
        vnp_TransactionDate: moment(order.createdAt).format('YYYYMMDDHHmmss'),
        vnp_CreateBy: req.user ? req.user.email : 'System',
        vnp_CreateDate: createDate,
        vnp_IpAddr: ipAddr
    };

    // Hàm tạo chữ ký hash (giống lúc tạo URL)
    const signData = vnp_RequestId + "|" + vnp_Version + "|" + vnp_Command + "|" + vnp_TmnCode + "|" + vnp_TransactionType + "|" + vnp_TxnRef + "|" + vnp_Amount + "|" + vnp_TransactionNo + "|" + vnp_TransactionDate + "|" + vnp_CreateBy + "|" + vnp_CreateDate + "|" + vnp_IpAddr + "|" + vnp_OrderInfo;
    const hmac = crypto.createHmac('sha512', vnp_HashSecret);
    const vnp_SecureHash = hmac.update(new Buffer(signData, 'utf-8')).digest('hex');
    data.vnp_SecureHash = vnp_SecureHash;

    try {
        const response = await axios.post(vnp_ApiUrl, data);
        // Cần parse string trả về của VNPay ra object để check ResponseCode
        const qs = require('qs');
        const parsedRes = qs.parse(response.data);
        return parsedRes.vnp_ResponseCode === '00';
    } catch (error) {
        console.error('VNPay Refund API Error:', error);
        return false;
    }
}

// Hàm hoàn trả tồn kho
async function restoreInventory(products) {
    for (let item of products) {
        await Product.findByIdAndUpdate(item.product, { $inc: { stock: item.quantity } });
    }
}