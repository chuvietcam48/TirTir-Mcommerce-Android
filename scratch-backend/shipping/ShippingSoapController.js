const xml2js = require('xml2js');
const ShippingQuoteService = require('./ShippingQuoteService');
const ShippingQuoteMapper = require('./ShippingQuoteMapper');
const SoapFaultFactory = require('./SoapFaultFactory');

class ShippingSoapController {
    async handleQuoteRequest(req, res) {
        // Enforce content type
        if (!req.is('text/xml') && !req.is('application/soap+xml')) {
            return res.status(415).send(SoapFaultFactory.clientFault('Unsupported Media Type: Must be XML'));
        }

        // Get user from token (assuming protect middleware sets req.user)
        const userId = req.user ? req.user._id : 'anonymous';

        try {
            // Parse XML body
            const parser = new xml2js.Parser({ explicitArray: false, ignoreAttrs: true });
            const result = await parser.parseStringPromise(req.body);

            // Navigate through SOAP Envelope -> Body -> GetShippingQuoteRequest
            let requestNode = result?.Envelope?.Body?.GetShippingQuoteRequest;
            
            // In case of namespaces like <tir:GetShippingQuoteRequest> or <soap:Envelope>
            if (!requestNode) {
                // Try finding with generic search or namespace stripping in a robust way
                // xml2js with explicitArray=false can have keys like 'soap:Envelope'
                const envelope = result['soap:Envelope'] || result['Envelope'];
                const body = envelope ? (envelope['soap:Body'] || envelope['Body']) : null;
                requestNode = body ? (body['tir:GetShippingQuoteRequest'] || body['GetShippingQuoteRequest']) : null;
            }

            if (!requestNode) {
                return res.status(400).send(SoapFaultFactory.clientFault('Malformed XML: Missing GetShippingQuoteRequest'));
            }

            // Extract fields (strip potential namespaces like tir:toDistrictId)
            const extractField = (node, fieldName) => {
                if (node[fieldName]) return node[fieldName];
                const nsKey = Object.keys(node).find(k => k.endsWith(`:${fieldName}`));
                return nsKey ? node[nsKey] : null;
            };

            const toDistrictId = extractField(requestNode, 'toDistrictId');
            const toWardCode = extractField(requestNode, 'toWardCode');
            const weightGrams = extractField(requestNode, 'weightGrams');
            const lengthCm = extractField(requestNode, 'lengthCm');
            const widthCm = extractField(requestNode, 'widthCm');
            const heightCm = extractField(requestNode, 'heightCm');
            const orderValue = extractField(requestNode, 'orderValue');

            if (!toDistrictId || !toWardCode || !weightGrams) {
                return res.status(400).send(SoapFaultFactory.clientFault('Missing required fields: toDistrictId, toWardCode, weightGrams'));
            }

            const requestData = {
                toDistrictId,
                toWardCode,
                weightGrams,
                lengthCm: lengthCm || 10,
                widthCm: widthCm || 10,
                heightCm: heightCm || 10,
                orderValue: orderValue || 0
            };

            // Get Quote from Service
            const quoteResult = await ShippingQuoteService.getQuote(userId, requestData);

            // Map to SOAP XML
            const xmlResponse = ShippingQuoteMapper.toSoapXml(quoteResult);

            res.set('Content-Type', 'text/xml');
            res.send(xmlResponse);

        } catch (error) {
            console.error('SOAP Controller Error:', error.message);
            // Distinguish between Client and Server faults based on error type if possible
            if (error.message.includes('No shipping services') || error.message.includes('Missing')) {
                return res.status(400).send(SoapFaultFactory.clientFault(error.message));
            }
            return res.status(500).send(SoapFaultFactory.serverFault(error.message));
        }
    }

    getWsdl(req, res) {
        // Send a static WSDL file if requested (optional but good for completeness)
        res.set('Content-Type', 'text/xml');
        res.send(`<?xml version="1.0" encoding="UTF-8"?>
<definitions name="TirTirShippingGateway"
             targetNamespace="http://tirtir.vn/shipping.wsdl"
             xmlns:tns="http://tirtir.vn/shipping.wsdl"
             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
             xmlns="http://schemas.xmlsoap.org/wsdl/">
    <!-- WSDL definitions for GetShippingQuote -->
</definitions>`);
    }
}

module.exports = new ShippingSoapController();
