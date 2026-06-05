const Shade = require("../models/shade.model");
const Product = require("../models/product.model");
const { rgbToLab, deltaE00 } = require("../utils/colorUtils");

exports.findBestMatch = async (req, res) => {
    try {
        const { r, g, b, l, a, b: bVal, skinType } = req.body;

        // H2 FIX: Input validation
        if (r !== undefined || g !== undefined || b !== undefined) {
            if ([r, g, b].some(v => typeof v !== 'number' || v < 0 || v > 255 || isNaN(v))) {
                return res.status(400).json({ message: "Invalid RGB values. Each must be a number between 0 and 255." });
            }
        }

        let userLab = { L: l, a: a, b: bVal };

        // If RGB provided but no LAB, convert it
        if ((l === undefined || a === undefined || bVal === undefined) && (r !== undefined && g !== undefined && b !== undefined)) {
            userLab = rgbToLab(r, g, b);
        }

        if (userLab.L === undefined) {
            return res.status(400).json({ message: "Missing color data (RGB or LAB required)" });
        }

        // Filter: Only recommend Cushions (exclude Lipsticks, Skincare, etc.)
        const shades = await Shade.find({ Shade_Type: 'Cushion' });
        
        // 1. Determine User Undertone
        // colorUtils.js returns STANDARD LAB (D65 illuminant):
        //   a is already centred at 0  (positive = red/pink, negative = green)
        //   b is already centred at 0  (positive = yellow/warm, negative = blue)
        // Typical skin tone values: a ≈ 5–20, b ≈ 10–35
        let userUndertone = 'Neutral';
        const a_n = userLab.a; // no offset needed — standard LAB
        const b_n = userLab.b;
        if (b_n > 10 && b_n > a_n * 1.2) {
            userUndertone = 'Warm';   // yellow strongly dominates
        } else if (a_n > 10 && a_n > b_n * 0.9) {
            userUndertone = 'Cool';   // red/pink dominates over yellow
        }
        // else: balanced → Neutral

        // 2. Oxidation Simulation (Target Shift)
        // If Oily, we want a shade that STARTS lighter (higher L).
        // Target_L = User_L + Shift.
        let targetL = userLab.L;
        let adjustmentNote = "Shade matched to your natural skin tone.";

        if (skinType === 'Oily') {
             targetL += 2.5;
             adjustmentNote = "Selected a slightly lighter shade for oily skin (prevents oxidation darkening).";
        }

        // Modified User Target for Matching
        const targetLab = { ...userLab, L: targetL };

        const results = shades.map(shade => {
            // Ensure shade has LAB values
            let shadeLab = { L: shade.L, a: shade.a, b: shade.b };
            
            // Fallback: if shade has no LAB but has RGB, convert
            if ((shade.L === undefined) && (shade.R !== undefined)) {
                shadeLab = rgbToLab(shade.R, shade.G, shade.B);
            }

            // Skip if no color data
            if (shadeLab.L === undefined) return null;

            // --- WEIGHTED SCORING SYSTEM ---
            
            // 1. DeltaE (Base Score) - Distance between Target and Shade
            const dE = deltaE00(targetLab, shadeLab);

            // 2. Undertone Penalty
            // If shade has 'Undertone' field, use it. Otherwise guess from name (C=Cool, N=Neutral, W=Warm)
            let shadeUndertone = shade.Undertone;
            if (!shadeUndertone) {
                if (shade.Shade_Code.endsWith('C')) shadeUndertone = 'Cool';
                else if (shade.Shade_Code.endsWith('W')) shadeUndertone = 'Warm';
                else shadeUndertone = 'Neutral';
            }
            
            // Penalty: 5 points if mismatch (e.g. Cool vs Warm)
            // Allow Neutral to match others with less penalty? 
            // User Rule: "UserUndertone != ProductUndertone -> +5"
            let pUndertone = 0;
            // Normalize strings
            const uU = userUndertone.toLowerCase();
            const sU = (shadeUndertone || '').toLowerCase();
            
            if (uU !== 'neutral' && sU !== 'neutral' && uU !== sU) {
                pUndertone = 5;
            } else if ((uU === 'neutral' && sU !== 'neutral') || (uU !== 'neutral' && sU === 'neutral')) {
                 // Slight penalty for Neutral <-> Warm/Cool mix
                 pUndertone = 2; 
            }

            // 3. Brightness Penalty
            // If Shade is darker than User (L_shade < L_user), penalize.
            // We compare against original User L, not Target L (which might be shifted).
            // Actually, if we shifted Target L up, we definitely don't want something darker than original user.
            let pBrightness = 0;
            if (shadeLab.L < userLab.L) {
                // Penalize 2 points per unit of darkness
                pBrightness = (userLab.L - shadeLab.L) * 2;
            }

            // Total Score
            const totalScore = dE + pUndertone + pBrightness;
            
            return { 
                ...shade.toObject(), 
                matchScore: totalScore,
                deltaE: dE,
                predictedUndertone: shadeUndertone,
                adjustmentNote: adjustmentNote
            };
        }).filter(item => item !== null);

        // Sort by lowest Total Score (best match)
        results.sort((x, y) => x.matchScore - y.matchScore);

        // Get Top 5
        const topMatches = results.slice(0, 5);

        // H1 FIX: Batch query all products at once instead of N+1
        const productIds = [...new Set(topMatches.map(m => m.Product_ID))];
        const products = await Product.find({ Product_ID: { $in: productIds } }).select('Product_ID Thumbnail_Images Name');
        const productMap = new Map(products.map(p => [p.Product_ID, p]));

        const finalResults = topMatches.map((match) => {
            const product = productMap.get(match.Product_ID);
            let imageUrl = match.Shade_Image;
            let productName = "";

            if (product) {
                if (!imageUrl) imageUrl = product.Thumbnail_Images;
                productName = product.Name;
            }

            return {
                ...match,
                Image_URL: imageUrl,
                Product_Name: productName
            };
        });

        res.json(finalResults);
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

exports.getShades = async (req, res) => {
    try {
        const limit = Math.min(Number(req.query.limit || 50), 200);

        const filter = {};
        if (req.query.productId) filter.Product_ID = req.query.productId;
        if (req.query.parentId) filter.Parent_ID = req.query.parentId;
        if (req.query.shadeType) filter.Shade_Type = req.query.shadeType;

        const data = await Shade.find(filter).limit(limit).sort({ No: 1 });
        res.json(data);
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

exports.getShadeById = async (req, res) => {
    try {
        const item = await Shade.findOne({ Shade_ID: req.params.shadeId });
        if (!item) return res.status(404).json({ message: "Shade not found" });
        res.json(item);
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

// ─── Admin Write Operations ───────────────────────────────────

exports.createShade = async (req, res) => {
    try {
        const shade = new Shade(req.body);
        await shade.save();
        res.status(201).json(shade);
    } catch (err) {
        res.status(400).json({ message: err.message });
    }
};

exports.updateShade = async (req, res) => {
    try {
        const updated = await Shade.findOneAndUpdate(
            { Shade_ID: req.params.shadeId },
            req.body,
            { new: true, runValidators: true }
        );
        if (!updated) return res.status(404).json({ message: 'Shade not found' });
        res.json(updated);
    } catch (err) {
        res.status(400).json({ message: err.message });
    }
};

exports.deleteShade = async (req, res) => {
    try {
        const deleted = await Shade.findOneAndDelete({ Shade_ID: req.params.shadeId });
        if (!deleted) return res.status(404).json({ message: 'Shade not found' });
        res.json({ message: 'Shade deleted successfully' });
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};
