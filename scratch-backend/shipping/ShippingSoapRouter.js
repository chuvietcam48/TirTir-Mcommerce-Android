const express = require('express');
const router = express.Router();
const ShippingSoapController = require('./ShippingSoapController');
const { protect } = require('../middleware/authMiddleware');

// Body parser specifically for XML
const xmlParser = express.text({ type: ['text/xml', 'application/soap+xml'] });

// SOAP WSDL
router.get('/shipping-quote', ShippingSoapController.getWsdl);

// SOAP Request (Protected by Firebase Auth)
router.post('/shipping-quote', protect, xmlParser, ShippingSoapController.handleQuoteRequest.bind(ShippingSoapController));

// Helper REST APIs for Android Dropdowns (Master Data)
// In a real scenario, this would call GHN APIs. 
// For demo, we proxy the GHN Location APIs.
const GhnCarrierClient = require('./GhnCarrierClient');

router.get('/locations/provinces', async (req, res) => {
    try {
        const response = await GhnCarrierClient.client.get('/shiip/public-api/master-data/province');
        res.json(response.data);
    } catch (error) {
        res.status(500).json({ success: false, message: 'Failed to load provinces' });
    }
});

router.get('/locations/districts', async (req, res) => {
    const provinceId = req.query.provinceId;
    try {
        const response = await GhnCarrierClient.client.get('/shiip/public-api/master-data/district', {
            params: { province_id: provinceId }
        });
        res.json(response.data);
    } catch (error) {
        res.status(500).json({ success: false, message: 'Failed to load districts' });
    }
});

router.get('/locations/wards', async (req, res) => {
    const districtId = req.query.districtId;
    try {
        const response = await GhnCarrierClient.client.get('/shiip/public-api/master-data/ward', {
            params: { district_id: districtId }
        });
        res.json(response.data);
    } catch (error) {
        res.status(500).json({ success: false, message: 'Failed to load wards' });
    }
});

module.exports = router;
