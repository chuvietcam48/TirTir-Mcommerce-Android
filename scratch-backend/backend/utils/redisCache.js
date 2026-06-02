'use strict';

const Redis = require('ioredis');
const crypto = require('crypto');
const logger = require('./logger');

let client = null;

/**
 * Initialize Redis client.
 * If REDIS_URL env var is not set, caching is silently disabled.
 */
function getClient() {
    if (client) return client;

    const redisUrl = process.env.REDIS_URL;
    if (!redisUrl) {
        // Redis not configured — caching disabled gracefully
        return null;
    }

    try {
        client = new Redis(redisUrl, {
            enableOfflineQueue: false,
            connectTimeout: 3000,
            maxRetriesPerRequest: 1,
            lazyConnect: true,
        });

        client.on('connect', () => logger.info('✅ Redis connected'));
        client.on('error', (err) => {
            logger.warn(`⚠️ Redis error (caching disabled): ${err.message}`);
            client = null; // Disable on persistent failure
        });
    } catch (err) {
        logger.warn(`⚠️ Redis init failed: ${err.message}`);
        client = null;
    }

    return client;
}

/**
 * Build a deterministic cache key from skin analysis parameters.
 * Normalizes and sorts concerns array so order doesn't matter.
 */
function buildCacheKey(skinType, skinTone, undertone, concerns) {
    const sortedConcerns = [...(concerns || [])].sort().join(',');
    const raw = `routine:${skinType}:${skinTone}:${undertone}:${sortedConcerns}`;
    return crypto.createHash('sha256').update(raw).digest('hex').slice(0, 32);
}

/**
 * Get cached value by key.
 * @returns {Promise<object|null>} Parsed JSON or null on miss/error
 */
async function getCache(key) {
    const redis = getClient();
    if (!redis) return null;
    try {
        const value = await redis.get(key);
        return value ? JSON.parse(value) : null;
    } catch {
        return null;
    }
}

/**
 * Set cache value with TTL.
 * @param {string} key
 * @param {object} value
 * @param {number} ttlSeconds Default: 24 hours
 */
async function setCache(key, value, ttlSeconds = 86400) {
    const redis = getClient();
    if (!redis) return;
    try {
        await redis.set(key, JSON.stringify(value), 'EX', ttlSeconds);
    } catch {
        // Silently ignore cache write errors
    }
}

module.exports = { getClient, buildCacheKey, getCache, setCache };
