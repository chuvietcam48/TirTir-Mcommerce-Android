class ShippingQuoteRepository {
    constructor() {
        this.quotes = new Map();
        this.TTL_MS = (parseInt(process.env.QUOTE_TTL_SECONDS) || 300) * 1000;
    }

    save(quoteId, data, userId) {
        const expiresAt = Date.now() + this.TTL_MS;
        this.quotes.set(quoteId, {
            ...data,
            userId,
            expiresAt
        });

        // Simple auto-cleanup
        setTimeout(() => {
            this.quotes.delete(quoteId);
        }, this.TTL_MS);
    }

    get(quoteId) {
        const quote = this.quotes.get(quoteId);
        if (!quote) return null;
        if (Date.now() > quote.expiresAt) {
            this.quotes.delete(quoteId);
            return null; // Expired
        }
        return quote;
    }
}

module.exports = new ShippingQuoteRepository();
