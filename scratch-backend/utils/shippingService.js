const axios = require('axios');

// --- Viettel Post SOAP endpoint (sandbox) ---
const VIETTEL_SOAP_URL = 'http://demo.viettelpost.vn/WebServiceGHTK/GiaoHangService';

// Fallback shipping fee table (VND) from config when SOAP times out
const FALLBACK_SHIPPING = {
  default: 35000,
  heavy: 50000,   // > 5 kg
};

/**
 * Call Viettel Post SOAP API to get real shipping fee.
 * @param {string} fromProvince   Province code of sender (warehouse)
 * @param {string} toProvince     Province code of receiver
 * @param {number} weightGrams    Package weight in grams
 * @param {number} totalPrice     Declared COD value
 * @returns {Promise<number>}     Shipping fee in VND
 */
async function fetchViettelShipping(fromProvince, toProvince, weightGrams, totalPrice) {
  const soapEnvelope = `<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xmlns:xsd="http://www.w3.org/2001/XMLSchema"
               xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetPrice xmlns="http://tempuri.org/">
      <SENDER_PROVINCE>${fromProvince}</SENDER_PROVINCE>
      <RECEIVER_PROVINCE>${toProvince}</RECEIVER_PROVINCE>
      <PRODUCT_TYPE>HH</PRODUCT_TYPE>
      <ORDER_SERVICE>VCN</ORDER_SERVICE>
      <ORDER_SERVICE_ADD></ORDER_SERVICE_ADD>
      <WEIGHT>${weightGrams}</WEIGHT>
      <MONEY_COLLECTION>${Math.round(totalPrice)}</MONEY_COLLECTION>
      <CHECK_UNIQUE>0</CHECK_UNIQUE>
    </GetPrice>
  </soap:Body>
</soap:Envelope>`;

  const response = await axios.post(VIETTEL_SOAP_URL, soapEnvelope, {
    headers: {
      'Content-Type': 'text/xml; charset=utf-8',
      'SOAPAction':   'http://tempuri.org/GetPrice',
    },
  });

  // Parse MONEY_TOTAL from XML response
  const match = response.data.match(/<MONEY_TOTAL>([\d.]+)<\/MONEY_TOTAL>/);
  if (match) return parseFloat(match[1]);
  throw new Error('Cannot parse Viettel response');
}

/**
 * Get shipping fee with a 5-second race timeout.
 * Returns { fee, isEstimated }.
 */
async function getShippingFee({ toProvince, weightGrams = 300, totalPrice = 0 }) {
  const FROM_PROVINCE = process.env.WAREHOUSE_PROVINCE || 'HCM';

  try {
    const fee = await Promise.race([
      fetchViettelShipping(FROM_PROVINCE, toProvince || 'HCM', weightGrams, totalPrice),
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error('SOAP_TIMEOUT')), 5000)
      ),
    ]);
    return { fee, isEstimated: false };
  } catch (err) {
    console.warn('[ShippingService] Viettel SOAP failed/timed-out:', err.message, '— using fallback.');
    const fallbackFee = weightGrams > 5000 ? FALLBACK_SHIPPING.heavy : FALLBACK_SHIPPING.default;
    return { fee: fallbackFee, isEstimated: true };
  }
}

module.exports = { getShippingFee };
