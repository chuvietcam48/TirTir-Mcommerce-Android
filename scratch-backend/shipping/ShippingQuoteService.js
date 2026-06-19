const { v4: uuidv4 } = require('uuid');
const GhnCarrierClient = require('./GhnCarrierClient');
const ShippingQuoteRepository = require('./ShippingQuoteRepository');

class ShippingQuoteService {
    async getQuote(userId, requestData) {
        const { toDistrictId, toWardCode, weightGrams, lengthCm, widthCm, heightCm, orderValue } = requestData;
        const fromDistrict = process.env.GHN_FROM_DISTRICT_ID || '1442'; // Default to a standard district if not set
        const fromWardCode = process.env.GHN_FROM_WARD_CODE || '20109'; // Default to a standard ward if not set

        // 1. Get Available Services
        const services = await GhnCarrierClient.getAvailableServices(fromDistrict, toDistrictId);
        if (!services || services.length === 0) {
            throw new Error("No shipping services available for this route.");
        }

        const quotesList = [];

        // 2. For each service, get Fee and Lead Time
        for (const service of services) {
            try {
                // Get Fee
                const feeData = await GhnCarrierClient.calculateFee(
                    service.service_id, 
                    fromDistrict, 
                    toDistrictId, 
                    toWardCode, 
                    weightGrams, 
                    lengthCm, 
                    widthCm, 
                    heightCm, 
                    orderValue
                );

                // Get Lead Time
                const leadTimeData = await GhnCarrierClient.getLeadTime(
                    fromDistrict, 
                    fromWardCode, 
                    toDistrictId, 
                    toWardCode, 
                    service.service_id
                );

                // Convert lead time timestamp to ISO date string
                const estimatedDate = new Date(leadTimeData.leadtime * 1000).toISOString();

                quotesList.push({
                    serviceId: service.service_id,
                    serviceName: service.short_name || 'Standard Delivery',
                    fee: feeData.total,
                    estimatedDeliveryTime: estimatedDate
                });
            } catch (err) {
                console.warn(`Could not get fee/lead time for service ${service.service_id}:`, err.message);
                // Continue with other services if one fails
            }
        }

        if (quotesList.length === 0) {
            throw new Error("Failed to calculate fee for all available services.");
        }

        // 3. Generate Quote ID and save to cache
        const quoteId = `ghn_quote_${uuidv4()}`;
        const expiresAt = new Date(Date.now() + (parseInt(process.env.QUOTE_TTL_SECONDS) || 300) * 1000).toISOString();
        
        const quoteResult = {
            quoteId,
            expiresAt,
            quotes: quotesList,
            requestHash: { toDistrictId, toWardCode, weightGrams } // For debugging/validation
        };

        ShippingQuoteRepository.save(quoteId, quoteResult, userId);

        return quoteResult;
    }
}

module.exports = new ShippingQuoteService();
