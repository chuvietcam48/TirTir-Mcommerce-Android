class ShippingQuoteMapper {
    static toSoapXml(quoteData) {
        let quotesXml = quoteData.quotes.map(q => `
        <quote>
          <serviceId>${q.serviceId}</serviceId>
          <serviceName>${q.serviceName}</serviceName>
          <fee>${q.fee}</fee>
          <estimatedDeliveryTime>${q.estimatedDeliveryTime}</estimatedDeliveryTime>
        </quote>`).join('');

        return `<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetShippingQuoteResponse>
      <carrier>GHN</carrier>
      <quoteId>${quoteData.quoteId}</quoteId>
      <expiresAt>${quoteData.expiresAt}</expiresAt>
      <quotes>${quotesXml}
      </quotes>
    </GetShippingQuoteResponse>
  </soap:Body>
</soap:Envelope>`;
    }
}

module.exports = ShippingQuoteMapper;
