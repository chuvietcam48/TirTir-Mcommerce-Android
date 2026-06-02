const { GoogleGenerativeAI } = require('@google/generative-ai');
const axios = require('axios');
const AiAnalysis = require('../models/ai_analysis.model');
const User = require('../models/user.model');
const Product = require('../models/product.model');
const { buildCacheKey, getCache, setCache } = require('../utils/redisCache');
const RoutineFeedback = require('../models/routine_feedback.model');

// FastAPI AI Microservice URL
const AI_SERVICE_URL = process.env.AI_SERVICE_URL || 'http://localhost:8000';
const AI_SERVICE_API_KEY = process.env.AI_SERVICE_API_KEY || '';

/**
 * ===== AI BEAUTY ADVISOR CONTROLLER =====
 * Uses Gemini AI Vision OR Local Python Script to analyze skin
 */

// ═══ Gemini AI Init + Circuit Breaker ═══════════════════════════════════════
let genAI = null;
let visionModel = null;

// Circuit breaker state: skip Gemini after consecutive failures
const geminiCircuit = {
    failures: 0,
    maxFailures: 3,
    cooldownMs: 5 * 60 * 1000,  // 5 minutes
    lastFailTime: 0,
    isOpen() {
        if (this.failures < this.maxFailures) return false;
        // Check if cooldown has passed
        if (Date.now() - this.lastFailTime > this.cooldownMs) {
            console.log('⚡ Gemini circuit breaker RESET — retrying');
            this.failures = 0;
            return false;
        }
        return true; // Still in cooldown
    },
    recordFailure() {
        this.failures++;
        this.lastFailTime = Date.now();
        if (this.failures >= this.maxFailures) {
            console.warn(`🔴 Gemini circuit OPEN — ${this.maxFailures} consecutive failures. Skipping for ${this.cooldownMs / 60000} min.`);
        }
    },
    recordSuccess() {
        this.failures = 0;
    }
};

const GEMINI_MODEL = 'gemini-1.5-flash'; // Free tier supported

const initializeGemini = () => {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey || apiKey === 'your_gemini_api_key_here' || apiKey.includes('your_')) {
        return false;
    }
    try {
        genAI = new GoogleGenerativeAI(apiKey);
        visionModel = genAI.getGenerativeModel({ model: GEMINI_MODEL });
        console.log(`✅ Gemini AI initialized (model: ${GEMINI_MODEL})`);
        return true;
    } catch (error) {
        console.error('❌ Failed to initialize Gemini AI:', error);
        return false;
    }
};

initializeGemini();

/** Sanitize string for safe inclusion in Gemini prompt (prevent injection) */
const sanitizeForPrompt = (str) => {
    if (!str || typeof str !== 'string') return '';
    return str.replace(/[\n\r`${}]/g, '').substring(0, 100).trim();
};

/**
 * Call FastAPI AI Microservice for skin analysis.
 * Model is pre-loaded in FastAPI — no per-request overhead.
 */
const callAIService = async (imageBase64) => {
    try {
        const response = await axios.post(`${AI_SERVICE_URL}/analyze`, {
            image_base64: imageBase64
        }, {
            timeout: 5000, // 5s — FastAPI should respond well within this on local/same network
            headers: {
                'Content-Type': 'application/json',
                ...(AI_SERVICE_API_KEY && { 'X-API-Key': AI_SERVICE_API_KEY })
            }
        });

        if (response.data.success) {
            return response.data.data;
        } else {
            throw new Error(response.data.error || 'AI Service returned failure');
        }
    } catch (error) {
        // Timeout — FastAPI is hanging/overloaded
        if (error.code === 'ECONNABORTED') {
            const err = new Error('AI Service đang quá tải, vui lòng thử lại sau.');
            err.statusCode = 504;
            throw err;
        }
        // FastAPI server is not running
        if (error.code === 'ECONNREFUSED') {
            const err = new Error('AI Service chưa chạy. Vui lòng khởi động FastAPI server (ai-service/).');
            err.statusCode = 503;
            throw err;
        }
        // FastAPI returned HTTP 4xx/5xx (e.g. model crash, bad image)
        if (error.response) {
            const status = error.response.status;
            const detail = error.response.data?.detail || error.response.data?.error || 'Lỗi không xác định';
            const err = new Error(`AI Service lỗi: ${detail}`);
            err.statusCode = status >= 500 ? 502 : 400;
            throw err;
        }
        throw error;
    }
};

/**
 * @route   GET /api/ai/history
 * @desc    Get analysis history for user
 * @access  Private
 */
exports.getHistory = async (req, res) => {
    try {
        if (!req.user) {
            return res.status(401).json({ message: "Unauthorized" });
        }

        const history = await AiAnalysis.find({ user: req.user.id })
            .sort({ createdAt: -1 })
            .limit(20);

        res.json({
            success: true,
            count: history.length,
            data: history
        });
    } catch (error) {
        res.status(500).json({ success: false, message: error.message });
    }
};

/**
 * @route   POST /api/ai/analyze-skin
 * @desc    Analyze skin using Gemini Vision API (Legacy/Alternative)
 */
exports.analyzeSkin = async (req, res) => {
    // Redirect logic to analyzeFace if python is preferred
    return exports.analyzeFace(req, res);
};

/**
 * @route   POST /api/ai/analyze-face
 * @desc    Analyze face using FastAPI AI Microservice
 * @access  Public (Save history if logged in)
 */
exports.analyzeFace = async (req, res) => {
    try {
        const { imageData } = req.body;

        if (!imageData) {
            return res.status(400).json({ success: false, message: 'Image data is required' });
        }

        // 1. Send base64 directly to FastAPI (no temp file needed)
        let analysisResult;
        try {
            analysisResult = await callAIService(imageData);
        } catch (err) {
            console.error('AI Service Error:', err.message);
            return res.status(err.statusCode || 500).json({
                success: false,
                message: 'AI Analysis failed',
                error: err.message
            });
        }

        // 2. Save to History + Update skinProfile snapshot (if user logged in)
        if (req.user) {
            // Save full analysis record
            await AiAnalysis.create({
                user: req.user.id,
                imageUrl: 'ai_analysis_result',
                analysisResult: {
                    skinTone:   analysisResult.skinTone,
                    undertone:  analysisResult.undertone,
                    skinType:   analysisResult.skinType,
                    concerns:   analysisResult.concerns || [],
                    confidence: analysisResult.confidence
                }
            });

            // Update quick-access skinProfile snapshot on User doc
            await User.findByIdAndUpdate(req.user.id, {
                skinProfile: {
                    skinTone:       analysisResult.skinTone,
                    undertone:      analysisResult.undertone,
                    skinType:       analysisResult.skinType,
                    concerns:       analysisResult.concerns || [],
                    confidence:     analysisResult.confidence,
                    lastAnalyzedAt: new Date()
                }
            });
        }

        res.json({
            success: true,
            data: analysisResult,
            saved: !!req.user // let frontend know if it was saved
        });

    } catch (error) {
        console.error('Analyze Face Error:', error);
        res.status(500).json({ success: false, message: error.message });
    }
};

/**
 * @route   POST /api/ai/recommend-routine
 * @desc    Get routine recommendations based on skin analysis using Gemini AI
 * @access  Public
 */
exports.recommendRoutine = async (req, res) => {
    try {
        let { skinType, concerns, skinTone, undertone, shadeMatchProduct } = req.body;

        // Fallback: if no body data but user has saved profile → use it
        if ((!skinType || !skinTone) && req.user) {
            const user = await User.findById(req.user.id).select('skinProfile');
            if (user?.skinProfile?.skinTone) {
                skinTone  = user.skinProfile.skinTone;
                skinType  = user.skinProfile.skinType || 'Normal';
                undertone = user.skinProfile.undertone;
                concerns  = user.skinProfile.concerns;
                console.log(`[AI] Using saved skin profile for user ${req.user.id}`);
            }
        }

        // 1. Validate Input
        if (!skinType || !skinTone) {
            return res.status(400).json({ success: false, message: 'Missing skin analysis data. Please scan first.' });
        }

        // === PHASE C: REDIS CACHE CHECK ===
        // Key = hash of skin profile + shade match — unique result per skin+shade combo
        const shadeMatchId = shadeMatchProduct?.Product_ID || 'none';
        const cacheKey = buildCacheKey(skinType, skinTone, undertone, concerns) + ':' + shadeMatchId;
        const cached = await getCache(cacheKey);
        if (cached) {
            console.log(`⚡ Cache HIT for routine [${skinTone}/${undertone}/${skinType}]`);
            return res.json({ success: true, data: cached, fromCache: true });
        }
        console.log(`🤖 Cache MISS — calling Gemini for [${skinTone}/${undertone}/${skinType}]`);

        // 2. Fetch Products from DB — include ALL relevant categories
        //    Real DB categories: Balm, Cushion, Gift Card, Makeup, Primer, Setting Spray, Skincare, Tint
        //    Most skincare products (cleanser, toner, serum, cream, etc.) are under "Skincare" category
        const products = await Product.find({
            Category: { $in: ['Skincare', 'Cushion', 'Balm', 'Primer', 'Setting Spray'] },
            Stock_Quantity: { $gt: 0 }
        }).select('Product_ID Name Category Description_Short Full_Description Skin_Type_Target Main_Concern Price Thumbnail_Images slug Is_Best_Seller Rating_Average Sold_Quantity');

        if (!products || products.length === 0) {
            return res.status(404).json({ success: false, message: 'No products available for recommendation' });
        }

        // 3. Keyword map: Gemini step category → search keywords for product Name matching
        const STEP_KEYWORDS = {
            'Cleanser':  ['cleans', 'balm', 'wash', 'foam', 'oil cleanser'],
            'Toner':     ['toner', 'tonic', 'mist', 'essence water'],
            'Serum':     ['serum', 'ampoule', 'essence', 'concentrate', 'oil'],
            'Cream':     ['cream', 'moistur', 'lotion', 'gel cream', 'cica'],
            'Sunscreen': ['sun', 'spf', 'uv', 'protection'],
            'Cushion':   ['cushion', 'foundation'],
            'Primer':    ['primer', 'base'],
        };

        // Map concern keywords to help match user concerns with product Main_Concern field
        const CONCERN_SYNONYMS = {
            'dryness':       ['dry', 'hydrat', 'moisture', 'dehydrat'],
            'acne':          ['acne', 'breakout', 'blemish', 'pimple', 'pore'],
            'dark circles':  ['dark circle', 'eye', 'bright', 'pigment'],
            'pigmentation':  ['pigment', 'dark spot', 'bright', 'whiten', 'even'],
            'redness':       ['red', 'calm', 'sooth', 'sensitive', 'cica'],
            'wrinkles':      ['wrinkle', 'anti-ag', 'firm', 'elasticity', 'collagen'],
            'oiliness':      ['oil', 'matte', 'sebum', 'control', 'pore'],
        };

        /**
         * Score a product based on how well it matches the user's skin profile.
         * Higher score = better fit. Returns 0 if product doesn't match step keywords at all.
         *
         * Scoring breakdown:
         *   Keyword relevance:   0 – 10  (must score > 0 to be a candidate)
         *   Skin type match:     0 – 15
         *   Concern match:       0 – 20  (most important differentiator)
         *   Popularity:          0 –  5  (Rating_Average + Sold_Quantity)
         *   Best seller bonus:   0 –  5
         *   ─────────────────────────────
         *   Max possible:        55
         */
        const scoreProduct = (product, stepCategory) => {
            const keywords = STEP_KEYWORDS[stepCategory] || [stepCategory.toLowerCase()];
            const nameLower = (product.Name || '').toLowerCase();
            const descLower = (product.Description_Short || '').toLowerCase();
            const fullDescLower = (product.Full_Description || '').toLowerCase();

            // ── Keyword relevance (0-10) ────────────────────────────────────
            let keywordScore = 0;
            for (const kw of keywords) {
                if (nameLower.includes(kw)) keywordScore += 7;       // Name match = high confidence
                else if (descLower.includes(kw)) keywordScore += 4;  // Short desc match
                else if (fullDescLower.includes(kw)) keywordScore += 2; // Full desc match
            }
            keywordScore = Math.min(keywordScore, 10); // cap at 10
            if (keywordScore === 0) return 0; // Not a candidate at all

            // ── Skin type match (0-30, or -20 penalty) — DOMINANT signal ──────
            let skinTypeScore = 0;
            const targetSkinLower = (product.Skin_Type_Target || '').toLowerCase();
            const userSkinLower = (skinType || '').toLowerCase();

            if (targetSkinLower) {
                if (targetSkinLower.includes(userSkinLower)) {
                    skinTypeScore = 30; // Exact match — highest priority
                } else if (
                    targetSkinLower.includes('all') ||
                    targetSkinLower.includes('every') ||
                    targetSkinLower.includes('any')
                ) {
                    skinTypeScore = 15; // Suitable for all skin types
                } else {
                    // Designed for a DIFFERENT skin type — heavy penalty
                    skinTypeScore = -20;
                }
            } else {
                skinTypeScore = 5; // No target specified — assume general
            }

            // ── Concern match (0-20) — the most important differentiator ────
            let concernScore = 0;
            const mainConcernLower = (product.Main_Concern || '').toLowerCase();

            if (concerns && concerns.length > 0) {
                for (const userConcern of concerns) {
                    const synonyms = CONCERN_SYNONYMS[userConcern.toLowerCase()] || [userConcern.toLowerCase()];
                    for (const syn of synonyms) {
                        if (mainConcernLower.includes(syn)) {
                            concernScore += 10; // Direct Main_Concern match
                            break;
                        } else if (nameLower.includes(syn) || descLower.includes(syn)) {
                            concernScore += 5;  // Mentioned in name/desc
                            break;
                        }
                    }
                }
            }
            concernScore = Math.min(concernScore, 20);

            // ── Popularity (0-5) ────────────────────────────────────────────
            const ratingScore = Math.min((product.Rating_Average || 0) / 5 * 3, 3); // 0-3 from rating
            const salesScore = Math.min((product.Sold_Quantity || 0) / 100 * 2, 2);  // 0-2 from sales
            const popularityScore = ratingScore + salesScore;

            // ── Best seller bonus (0-5) ─────────────────────────────────────
            const bestSellerBonus = product.Is_Best_Seller ? 5 : 0;

            return keywordScore + skinTypeScore + concernScore + popularityScore + bestSellerBonus;
        };

        /** Find the BEST product for a routine step using ranked scoring */
        const findProductForStep = (stepCategory, usedIds) => {
            // SPECIAL: For Cushion step, always prefer the shade-matched product
            if (stepCategory === 'Cushion' && shadeMatchProduct?.Product_ID) {
                const matched = products.find(p =>
                    p.Product_ID === shadeMatchProduct.Product_ID && !usedIds.has(p._id.toString())
                );
                if (matched) return matched;
                const byName = products.find(p =>
                    p.Name?.toLowerCase().includes(shadeMatchProduct.Product_Name?.toLowerCase().substring(0, 15) || '') &&
                    !usedIds.has(p._id.toString())
                );
                if (byName) return byName;
            }

            // Score ALL candidates and pick the best one
            const candidates = products
                .filter(p => !usedIds.has(p._id.toString()))
                .map(p => ({ product: p, score: scoreProduct(p, stepCategory) }))
                .filter(c => c.score > 0)            // Discard wrong-skin-type products (negative score)
                .sort((a, b) => b.score - a.score);

            if (candidates.length > 0) {
                console.log(`    [Score] ${stepCategory}/${skinType}: Top 3 → ${candidates.slice(0, 3).map(c => `${c.product.Name} (${c.score})`).join(' | ')}`);
                return candidates[0].product;
            }

            // Relaxed fallback: allow all-skin-type products even if score was filtered
            const relaxed = products
                .filter(p => !usedIds.has(p._id.toString()))
                .map(p => ({ product: p, score: scoreProduct(p, stepCategory) }))
                .filter(c => c.score > -5) // Allow "all skin types" which has score >= 5
                .sort((a, b) => b.score - a.score);
            if (relaxed.length > 0) return relaxed[0].product;

            // Last resort for Cushion: match by Category directly
            if (stepCategory === 'Cushion') {
                return products.find(p => p.Category === 'Cushion' && !usedIds.has(p._id.toString())) || null;
            }
            return null;
        };

        // 4. Build product list for Gemini — annotate with skin type suitability
        //    Partition into: (a) products that match user's skin type, (b) all-skin-type, (c) others
        const buildProductAnnotation = (p) => {
            const target = (p.Skin_Type_Target || '').toLowerCase();
            const userSkin = (skinType || '').toLowerCase();
            let suitability = '';
            if (target.includes(userSkin)) suitability = ` [RECOMMENDED for ${skinType}]`;
            else if (target.includes('all') || target.includes('every') || !target) suitability = ' [Suitable for all skin types]';
            else suitability = ` [Designed for ${p.Skin_Type_Target} — less ideal]`;
            return `- "${p.Name}" (${p.Category}${p.Main_Concern ? ', targets: ' + p.Main_Concern : ''}${p.Description_Short ? ', ' + p.Description_Short.substring(0, 50) : ''})${suitability}`;
        };

        // Sort: skin-type-matched first, then all-skin, then others
        const sortedProducts = [...products].sort((a, b) => {
            const score = (p) => {
                const t = (p.Skin_Type_Target || '').toLowerCase();
                const u = (skinType || '').toLowerCase();
                if (t.includes(u)) return 2;
                if (t.includes('all') || !t) return 1;
                return 0;
            };
            return score(b) - score(a);
        });

        const productListStr = sortedProducts.map(p => buildProductAnnotation(p)).join('\n');

        const prompt = `
You are a licensed dermatologist and skincare advisor for TirTir — a premium Korean beauty brand.

A customer's skin has been analyzed. Create a PERSONALIZED skincare routine.

CUSTOMER SKIN PROFILE:
- Skin Type: ${skinType} ← CRITICAL: All recommended products MUST be suitable for ${skinType} skin
- Skin Tone: ${skinTone}
- Undertone: ${undertone || 'Not detected'}
- Detected Concerns: ${concerns && concerns.length > 0 ? concerns.join(', ') : 'None'}
${shadeMatchProduct ? `- Shade Match Result: ${sanitizeForPrompt(shadeMatchProduct.Product_Name)} (${sanitizeForPrompt(shadeMatchProduct.Shade_Name)} ${sanitizeForPrompt(shadeMatchProduct.Shade_Code)}) — use THIS product for the Cushion step` : ''}

AVAILABLE TIRTIR PRODUCTS (sorted by suitability for ${skinType} skin):
${productListStr}

ROUTINE STEP CATEGORIES (use exactly these values for "step"): Cleanser, Toner, Serum, Cream, Sunscreen, Cushion

CRITICAL RULES:
1. ONLY select products marked [RECOMMENDED for ${skinType}] or [Suitable for all skin types]
2. DO NOT select products marked [Designed for X — less ideal] unless there is absolutely no alternative
3. Each product must directly address ${skinType} skin needs
4. For ${skinType} skin specifically: ${skinType === 'Oily' ? 'prefer lightweight, oil-free, mattifying products' : skinType === 'Dry' ? 'prefer deeply hydrating, nourishing, barrier-repair products' : skinType === 'Sensitive' ? 'prefer gentle, fragrance-free, soothing products' : skinType === 'Combination' ? 'prefer balancing products, lightweight hydration' : 'prefer well-balanced, gentle products'}

INSTRUCTIONS:
1. Select 3 to 5 routine steps.
2. For each step, pick ONE product from the list. Use its EXACT name in "product_name".
3. Provide a clinical reason specific to ${skinType} skin.
4. Write a dermatologist_note: 2-sentence clinical assessment.
5. Write expert_advice: 1-2 sentence routine summary mentioning ${skinType} skin.
6. Estimate current skin metrics (0-100) and predict improvement after 28 days.
7. Return ONLY a raw JSON object — no Markdown, no code blocks:

{
  "routine": [
    {
      "step": "Cleanser",
      "product_name": "Exact product name from the list above",
      "reason": "Clinical reason specific to ${skinType} skin",
      "application_tip": "How to apply correctly"
    }
  ],
  "expert_advice": "...",
  "dermatologist_note": "...",
  "skin_metrics": { "hydration": 55, "elasticity": 70, "pigmentation": 75, "texture": 68, "sensitivity": 35 },
  "skin_evolution": {
    "current": { "hydration": 55, "texture": 52 },
    "predicted": { "hydration": 72, "texture": 75 }
  }
}`;

        // 4. Call Gemini API (with circuit breaker)
        let recommendationData;

        if (genAI && visionModel && !geminiCircuit.isOpen()) {
            try {
                const model = genAI.getGenerativeModel({ model: GEMINI_MODEL });
                const result = await model.generateContent(prompt);
                const response = await result.response;
                // Strip markdown code fences Gemini sometimes wraps around JSON
                const rawText = response.text().replace(/```json\s*|```\s*/g, '').trim();

                try {
                    recommendationData = JSON.parse(rawText);
                    geminiCircuit.recordSuccess();
                    console.log('✅ Gemini response parsed successfully');
                } catch (parseError) {
                    // Log the actual text so it can be debugged
                    console.error('❌ Gemini JSON parse failed:', parseError.message);
                    console.error('   Raw Gemini output (first 500 chars):', rawText.substring(0, 500));
                    geminiCircuit.recordFailure();
                    recommendationData = null;
                }

            } catch (aiError) {
                const errMsg = aiError?.message || String(aiError);
                // Distinguish quota/rate-limit errors from network/other errors
                if (errMsg.includes('429') || errMsg.includes('quota') || errMsg.includes('RESOURCE_EXHAUSTED')) {
                    console.warn('⚠️ Gemini quota/rate-limit hit — falling back to heuristic:', errMsg);
                } else {
                    console.error('❌ Gemini Generation Error:', errMsg);
                }
                geminiCircuit.recordFailure();
                recommendationData = null;
            }
        } else if (geminiCircuit.isOpen()) {
            console.log('⏭️ Gemini circuit OPEN — skipping, using heuristic directly');
        }

        // 5. Fallback Logic (Heuristic) if AI is disabled or fails
        if (!recommendationData) {
            console.log('Falling back to Heuristic Recommendation');
            const usedFallback = new Set();
            const routine = [];

            // Use keyword matching to find products for each step category
            const fallbackSteps = [
                { step: 'Cleanser', reason: `Gentle cleansing is essential for ${skinType} skin to remove impurities without over-stripping.`, application_tip: 'Massage gently onto damp face for 30 seconds, then rinse with lukewarm water.' },
                { step: 'Toner', reason: `Rebalances skin pH and prepares ${skinType} skin for better absorption of subsequent products.`, application_tip: 'Apply with cotton pad or pat directly onto face after cleansing.' },
                { step: 'Serum', reason: `Targets ${concerns?.[0] || 'overall skin health'} with concentrated active ingredients.`, application_tip: 'Apply 2-3 drops, press gently into skin, do not rub.' },
                { step: 'Cream', reason: `Locks in moisture and creates a protective barrier suited for ${skinType} skin.`, application_tip: 'Apply as the final skincare step, gently massage in upward motions.' },
                { step: 'Cushion', reason: `Provides coverage and sun protection while giving a natural, dewy finish.`, application_tip: 'Pat cushion puff onto face, building coverage gradually from center outward.' }
            ];

            for (const fs of fallbackSteps) {
                const product = findProductForStep(fs.step, usedFallback);
                if (product) {
                    usedFallback.add(product._id.toString());
                    routine.push({ ...fs, product_name: product.Name });
                } else {
                    routine.push(fs); // still include step even without product
                }
            }

            // ── Dynamic skin metrics based on skin profile analysis ──────────
            // Uses weighted scoring instead of simple if/else hardcoding
            const baseMetrics = { hydration: 55, elasticity: 70, pigmentation: 75, texture: 68, sensitivity: 35 };

            // Skin type adjustments
            const skinTypeModifiers = {
                'Dry':         { hydration: -17, elasticity: -5, sensitivity: +15 },
                'Oily':        { hydration: +7,  texture: -8,  sensitivity: -5 },
                'Combination': { hydration: -5,  texture: -5 },
                'Sensitive':   { sensitivity: +30, hydration: -10, elasticity: -8 },
                'Normal':      {} // baseline
            };

            // Concern adjustments (additive)
            const concernModifiers = {
                'Acne':          { texture: -20, sensitivity: +10 },
                'Dark Circles':  { pigmentation: -15, elasticity: -5 },
                'Dryness':       { hydration: -15, texture: -5 },
                'Pigmentation':  { pigmentation: -25 },
                'Redness':       { sensitivity: +20, pigmentation: -5 },
                'Wrinkles':      { elasticity: -20, texture: -10, hydration: -8 },
                'Oiliness':      { hydration: +10, texture: -8 },
                'Pores':         { texture: -15 },
            };

            // Apply skin type modifiers
            const mods = skinTypeModifiers[skinType] || {};
            for (const [k, v] of Object.entries(mods)) {
                baseMetrics[k] = Math.max(10, Math.min(95, baseMetrics[k] + v));
            }

            // Apply each user concern
            if (concerns && concerns.length > 0) {
                for (const concern of concerns) {
                    const cMods = concernModifiers[concern] || {};
                    for (const [k, v] of Object.entries(cMods)) {
                        if (baseMetrics[k] !== undefined) {
                            baseMetrics[k] = Math.max(10, Math.min(95, baseMetrics[k] + v));
                        }
                    }
                }
            }

            // Predicted improvement after 28 days (10-25% improvement)
            const improvement = (val) => Math.min(95, val + Math.round((95 - val) * 0.35));

            recommendationData = {
                routine,
                expert_advice: `Based on your ${skinType} skin analysis${concerns?.length ? ` with ${concerns.join(' and ')}` : ''}, we've curated a ${routine.filter(r => r.product_name).length}-step routine. For best results, follow consistently morning and night.`,
                dermatologist_note: `Your skin presents characteristics of ${skinType} type${ concerns?.length ? ` with notable ${concerns.join(', ')}` : '' }. A gentle, consistent routine targeting your specific concerns is the foundation of healthy skin.`,
                skin_metrics: baseMetrics,
                skin_evolution: {
                    current: { hydration: baseMetrics.hydration, texture: baseMetrics.texture },
                    predicted: { hydration: improvement(baseMetrics.hydration), texture: improvement(baseMetrics.texture) }
                },
                isHeuristicGenerated: true // Flag so frontend knows this is heuristic, not Gemini
            };
        }

        // 6. Hydrate Product Details — use keyword-based Name matching
        //    Gemini may return product_name or just step category. We match both ways.
        const usedProductIds = new Set();
        const enrichedRoutine = recommendationData.routine.map((step) => {
            const stepCat = (step.step || '').trim();
            let product = null;

            // Strategy A: If Gemini returned a product_name, find exact match by name
            if (step.product_name) {
                product = products.find(p =>
                    !usedProductIds.has(p._id.toString()) &&
                    p.Name?.toLowerCase() === step.product_name.toLowerCase()
                );
                // Fuzzy: check if name contains the suggested name
                if (!product) {
                    product = products.find(p =>
                        !usedProductIds.has(p._id.toString()) &&
                        p.Name?.toLowerCase().includes(step.product_name.toLowerCase().substring(0, 15))
                    );
                }
            }

            // Strategy B: Keyword-based matching using STEP_KEYWORDS
            if (!product) {
                product = findProductForStep(stepCat, usedProductIds);
            }

            if (product) usedProductIds.add(product._id.toString());

            return { ...step, product: product ? { Name: product.Name, Category: product.Category, Price: product.Price, Thumbnail_Images: product.Thumbnail_Images, slug: product.slug, _id: product._id, Product_ID: product.Product_ID } : null };
        });

        console.log(`[AI Routine] Matched ${enrichedRoutine.filter(r => r.product).length}/${enrichedRoutine.length} steps with real products`);

        // 7. Calculate total routine price (only steps with real products)
        const totalPrice = enrichedRoutine
            .filter(r => r.product)
            .reduce((sum, r) => sum + (r.product?.Price || 0), 0);

        const responseData = {
            routine: enrichedRoutine,
            advice: recommendationData.expert_advice,
            dermatologistNote: recommendationData.dermatologist_note || null,
            skinMetrics: recommendationData.skin_metrics || null,
            skinEvolution: recommendationData.skin_evolution || null,
            totalPrice,
            skinType,
            isHeuristicGenerated: recommendationData.isHeuristicGenerated || false, // C4 FIX
        };

        // M3 FIX: Shorter TTL for heuristic results so Gemini recovery is reflected quickly
        const cacheTTL = responseData.isHeuristicGenerated ? 300 : 7200; // 5min vs 2h
        await setCache(cacheKey, responseData, cacheTTL);
        console.log(`Cached routine for [${skinTone}/${undertone}/${skinType}]`);

        res.json({ success: true, data: responseData, fromCache: false });

    } catch (error) {
        console.error('Recommend Routine Error:', error);
        res.status(500).json({ success: false, message: error.message });
    }
};

/**
 * @route   GET /api/v1/ai/latest-profile
 * @desc    Get the user's latest skin analysis snapshot
 * @access  Private
 */
exports.getLatestProfile = async (req, res) => {
    try {
        const user = await User.findById(req.user.id).select('skinProfile');

        if (!user?.skinProfile?.skinTone) {
            return res.json({ success: true, data: null, message: 'No skin analysis found. Please scan first.' });
        }

        // Also fetch the last 5 history records
        const history = await AiAnalysis.find({ user: req.user.id })
            .sort({ createdAt: -1 })
            .limit(5)
            .select('analysisResult createdAt');

        res.json({
            success: true,
            data: {
                skinProfile: user.skinProfile,
                history
            }
        });
    } catch (error) {
        res.status(500).json({ success: false, message: error.message });
    }
};

/**
 * @route   POST /api/ai/save-result
 * @desc    Save a pre-computed scan result (no image needed).
 *          Used when a guest user scanned before login — the result is stored
 *          in localStorage and flushed here after authentication.
 * @access  Private
 */
exports.saveResult = async (req, res) => {
    try {
        const { skinTone, undertone, skinType, concerns, confidence, routine } = req.body;

        if (!skinTone || !undertone) {
            return res.status(400).json({ success: false, message: 'skinTone and undertone are required' });
        }

        const concernList = Array.isArray(concerns) ? concerns : [];

        // Save full analysis record (imageUrl marks it as a session-restored result)
        await AiAnalysis.create({
            user: req.user.id,
            imageUrl: 'session_restored',
            analysisResult: {
                skinTone,
                undertone,
                skinType: skinType || 'Normal',
                concerns: concernList,
                confidence: confidence || 0.85,
            },
            recommendedRoutine: Array.isArray(routine)
                ? routine.filter(s => s.productId || s.product_id).map(s => ({
                    step: s.step,
                    productName: s.product?.Name || s.productName || '',
                    usage: s.reason || s.application_tip || '',
                }))
                : [],
        });

        // Update quick-access skinProfile snapshot on User doc
        await User.findByIdAndUpdate(req.user.id, {
            skinProfile: {
                skinTone,
                undertone,
                skinType: skinType || 'Normal',
                concerns: concernList,
                confidence: confidence || 0.85,
                lastAnalyzedAt: new Date(),
            },
        });

        res.json({ success: true, message: 'Scan result saved to your profile' });
    } catch (error) {
        res.status(500).json({ success: false, message: error.message });
    }
};

/**
 * @route   GET /api/ai/health
 * @desc    Health check — also checks FastAPI service
 */
exports.healthCheck = async (req, res) => {
    let aiServiceStatus = 'unknown';
    try {
        const aiHealth = await axios.get(`${AI_SERVICE_URL}/health`, { timeout: 3000 });
        aiServiceStatus = aiHealth.data.skin_analyzer_loaded ? 'ready' : 'loading';
    } catch {
        aiServiceStatus = 'offline';
    }

    res.json({
        status: 'OK',
        gemini: !!visionModel,
        aiService: aiServiceStatus,
        aiServiceUrl: AI_SERVICE_URL
    });
};

/**
 * @route   POST /api/ai/routine-feedback
 * @desc    Record user feedback on a recommended routine product
 * @access  Private (requires login)
 */
exports.submitRoutineFeedback = async (req, res) => {
    try {
        const { step, productId, productName, rating, feedback, action, skinProfile } = req.body;

        if (!step || !productId || !rating) {
            return res.status(400).json({ success: false, message: 'step, productId, and rating are required' });
        }

        const entry = await RoutineFeedback.create({
            user: req.user.id,
            skinProfile: skinProfile || {},
            step,
            productId,
            productName: productName || '',
            rating: Math.min(Math.max(rating, 1), 5),
            feedback: feedback || '',
            action: action || 'kept'
        });

        console.log(`[Feedback] User ${req.user.id} rated ${step}/${productId}: ${rating}/5 (${action})`);

        res.status(201).json({ success: true, data: entry });
    } catch (error) {
        console.error('Routine Feedback Error:', error);
        res.status(500).json({ success: false, message: 'Failed to save feedback' });
    }
};
