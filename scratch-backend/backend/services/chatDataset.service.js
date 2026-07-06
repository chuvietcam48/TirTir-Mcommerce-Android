'use strict';
const { GoogleGenerativeAI } = require('@google/generative-ai');
const { getFirestore } = require('./firebaseAdmin.service');

let _cache = null;
let _cacheTime = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

// ── Cache management ──────────────────────────────────────────────────────────

async function getDataset() {
    const now = Date.now();
    if (_cache && now - _cacheTime < CACHE_TTL_MS) return _cache;

    const db = getFirestore();
    if (!db) {
        _cache = [];
        _cacheTime = now;
        return _cache;
    }

    try {
        const snap = await db.collection('suggestedQuestions').get();
        _cache = snap.docs
            .map(doc => ({ id: doc.id, ...doc.data() }))
            .filter(item => item.isActive !== false)
            .sort((a, b) => (a.priority || 999) - (b.priority || 999));
        _cacheTime = now;
    } catch (err) {
        console.error('[chatDataset] getDataset error:', err.message);
        _cache = _cache || [];
    }
    return _cache;
}

function invalidateCache() {
    _cache = null;
    _cacheTime = 0;
}

// ── Text normalization ────────────────────────────────────────────────────────

function normalize(text) {
    return (text || '')
        .toLowerCase()
        .trim()
        .replace(/[.,?!;:'"()\-]/g, '')
        .replace(/\s+/g, ' ');
}

// ── Layer 1: Exact alias match ────────────────────────────────────────────────

function matchAlias(userMessage, dataset) {
    const norm = normalize(userMessage);
    for (const item of dataset) {
        for (const alias of (item.aliases || [])) {
            if (norm === normalize(alias)) {
                return { item, method: 'alias', confidence: 1.0 };
            }
        }
    }
    return null;
}

// ── Layer 2: Keyword score match ──────────────────────────────────────────────

function matchKeyword(userMessage, dataset) {
    const norm = normalize(userMessage);
    let best = null;
    let bestScore = 0;

    for (const item of dataset) {
        let score = 0;
        for (const kw of (item.keywords || [])) {
            if (kw && norm.includes(normalize(kw))) score++;
        }
        if (score > bestScore) {
            bestScore = score;
            best = item;
        }
    }

    // Short messages need only 1 keyword match; longer messages need 2+
    const threshold = norm.length < 25 ? 1 : 2;
    if (bestScore >= threshold && best) {
        return { item: best, method: 'keyword', confidence: 0.75 };
    }
    return null;
}

// ── Layer 3: Gemini semantic intent classifier ────────────────────────────────
// Sends ONLY intentCode + question to Gemini — never exposes full answer text.
// Returns {intentCode, confidence} or no_match. Max 6s timeout.

async function classifyIntent(userMessage, dataset) {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey || apiKey.includes('your_') || !dataset.length) return null;

    const intentLines = dataset
        .map(item => `${item.intentCode}: "${item.question}"`)
        .join('\n');

    const prompt =
`You are an intent classifier for a K-beauty skincare chatbot (TirTir brand).
Match the user message to the most semantically similar known intent.
Consider multilingual similarity — Vietnamese and English questions about the same topic MUST match.

User message: "${userMessage}"

Known intents (intentCode: canonical question):
${intentLines}

Reply with ONLY valid JSON on one line, no explanation, no markdown:
{"intentCode":"matched_code","confidence":0.95}
OR if no semantic match:
{"intentCode":"no_match","confidence":0.0}

Rules:
- confidence is 0.0–1.0
- Match by MEANING, not just shared words
- "Serum before or after cream?" MUST match "Should serum be used before or after moisturizer?"
- Return no_match only when the topic is genuinely unrelated to skincare, orders, or TirTir products`;

    try {
        const genAI = new GoogleGenerativeAI(apiKey);
        const model = genAI.getGenerativeModel({ model: 'gemini-1.5-flash' });

        const resultPromise = model.generateContent({
            contents: [{ role: 'user', parts: [{ text: prompt }] }],
            generationConfig: { maxOutputTokens: 80, temperature: 0.05 }
        });
        const timeoutPromise = new Promise((_, reject) =>
            setTimeout(() => reject(new Error('classifier_timeout')), 6000)
        );

        const result = await Promise.race([resultPromise, timeoutPromise]);
        const raw = result.response.text().trim();

        // Extract the JSON object — ignore any surrounding text
        const match = raw.match(/\{[^}]+\}/);
        if (!match) return null;

        const parsed = JSON.parse(match[0]);
        const { intentCode, confidence } = parsed;

        if (!intentCode || intentCode === 'no_match' || (confidence || 0) < 0.70) return null;

        const found = dataset.find(d => d.intentCode === intentCode);
        if (!found) return null;

        return { item: found, method: 'semantic', confidence };
    } catch (err) {
        if (err.message !== 'classifier_timeout') {
            console.error('[chatDataset] classifyIntent error:', err.message);
        }
        return null;
    }
}

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Run all 3 matching layers in order.
 * Returns { item, method, confidence } or null (= out of dataset).
 */
async function matchDataset(userMessage) {
    if (!userMessage || !userMessage.trim()) return null;

    const dataset = await getDataset();
    if (!dataset.length) return null;

    return (
        matchAlias(userMessage, dataset) ||
        matchKeyword(userMessage, dataset) ||
        (await classifyIntent(userMessage, dataset))
    );
}

module.exports = { getDataset, matchDataset, invalidateCache };
