const NodeCache = require('node-cache');

// Standard TTL 10 minutes, check period 2 minutes
const cache = new NodeCache({ stdTTL: 600, checkperiod: 120 });

exports.cacheMiddleware = (duration) => (req, res, next) => {
    // Skip caching for non-GET requests
    if (req.method !== 'GET') {
        return next();
    }

    const key = req.originalUrl;
    const cachedResponse = cache.get(key);

    if (cachedResponse) {
        return res.json(cachedResponse);
    } else {
        // Override res.json to intercept response and cache it
        const originalSend = res.json;
        res.json = (body) => {
            if (res.statusCode === 200) {
                cache.set(key, body, duration);
            }
            originalSend.call(res, body);
        };
        next();
    }
};

exports.clearCache = (keyPattern) => {
    const keys = cache.keys();
    const matches = keys.filter(k => k.includes(keyPattern));
    if (matches.length > 0) {
        cache.del(matches);
        console.log(`Cleared cache for keys matching: ${keyPattern}`);
    }
};
