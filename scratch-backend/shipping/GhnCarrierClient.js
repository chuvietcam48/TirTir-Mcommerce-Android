const axios = require('axios');

class GhnCarrierClient {
    constructor() {
        this.baseUrl = process.env.GHN_BASE_URL || 'https://online-gateway.ghn.vn';
        this.token = process.env.GHN_TOKEN;
        this.shopId = process.env.GHN_SHOP_ID;

        this.client = axios.create({
            baseURL: this.baseUrl,
            headers: {
                'Content-Type': 'application/json',
                'Token': this.token
            },
            timeout: 5000 // GHN timeout -> SOAP Server Fault
        });
    }

    async getAvailableServices(fromDistrict, toDistrict) {
        try {
            const response = await this.client.post('/shiip/public-api/v2/shipping-order/available-services', {
                shop_id: parseInt(this.shopId, 10),
                from_district: parseInt(fromDistrict, 10),
                to_district: parseInt(toDistrict, 10)
            });
            if (response.data && response.data.code === 200) {
                return response.data.data;
            }
            throw new Error(`GHN Available Services Error: ${response.data.message}`);
        } catch (error) {
            console.error('GHN getAvailableServices API Error:', error.message);
            throw error;
        }
    }

    async calculateFee(serviceId, fromDistrict, toDistrict, toWardCode, weight, length, width, height, orderValue) {
        try {
            const response = await this.client.post('/shiip/public-api/v2/shipping-order/fee', {
                service_id: parseInt(serviceId, 10),
                insurance_value: parseInt(orderValue, 10),
                from_district_id: parseInt(fromDistrict, 10),
                to_district_id: parseInt(toDistrict, 10),
                to_ward_code: toWardCode,
                weight: parseInt(weight, 10),
                length: parseInt(length, 10),
                width: parseInt(width, 10),
                height: parseInt(height, 10)
            }, {
                headers: {
                    'ShopId': this.shopId
                }
            });
            if (response.data && response.data.code === 200) {
                return response.data.data;
            }
            throw new Error(`GHN Calculate Fee Error: ${response.data.message}`);
        } catch (error) {
            console.error('GHN calculateFee API Error:', error.message);
            throw error;
        }
    }

    async getLeadTime(fromDistrict, fromWardCode, toDistrict, toWardCode, serviceId) {
        try {
            const response = await this.client.post('/shiip/public-api/v2/shipping-order/leadtime', {
                from_district_id: parseInt(fromDistrict, 10),
                from_ward_code: fromWardCode,
                to_district_id: parseInt(toDistrict, 10),
                to_ward_code: toWardCode,
                service_id: parseInt(serviceId, 10)
            }, {
                headers: {
                    'ShopId': this.shopId
                }
            });
            if (response.data && response.data.code === 200) {
                return response.data.data;
            }
            throw new Error(`GHN Lead Time Error: ${response.data.message}`);
        } catch (error) {
            console.error('GHN getLeadTime API Error:', error.message);
            throw error;
        }
    }
}

module.exports = new GhnCarrierClient();
