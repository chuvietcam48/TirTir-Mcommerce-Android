class SoapFaultFactory {
    static createFault(faultCode, faultString) {
        return `<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Body>
        <soap:Fault>
            <faultcode>${faultCode}</faultcode>
            <faultstring>${faultString}</faultstring>
        </soap:Fault>
    </soap:Body>
</soap:Envelope>`;
    }

    static clientFault(message) {
        return this.createFault('soap:Client', message);
    }

    static serverFault(message) {
        return this.createFault('soap:Server', message);
    }
}

module.exports = SoapFaultFactory;
