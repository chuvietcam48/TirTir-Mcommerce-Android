const Product = require('../models/product.model');
const Shade = require('../models/shade.model');
const sharp = require('sharp');

// 1. GET /api/v1/admin/smart/suggest-parent
exports.suggestParent = async (req, res) => {
    try {
        const { productName, category } = req.query;
        if (!productName) return res.json([]);

        // Fuzzy search products name for matching Parent_ID logic
        // We'll aggregate to find unique Parent_IDs and pick the most relevant
        const regex = new RegExp(productName.split(' ')[0], 'i'); // Simple keyword match
        
        const pipeline = [
            { 
                $match: { 
                    Parent_ID: { $exists: true, $ne: "" },
                    Name: { $regex: regex }
                } 
            },
            {
                $group: {
                    _id: "$Parent_ID",
                    lineName: { $first: "$Name" },
                    count: { $sum: 1 }
                }
            },
            { $sort: { count: -1 } },
            { $limit: 3 },
            {
                $project: {
                    _id: 0,
                    Parent_ID: "$_id",
                    lineName: 1,
                    score: "$count"
                }
            }
        ];

        const suggestions = await Product.aggregate(pipeline);
        res.json(suggestions);
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

// 2. POST /api/v1/admin/smart/generate-ids
exports.generateIds = async (req, res) => {
    try {
        const { productName, category, parentID } = req.body;
        
        // CAT_CODE
        const catMap = {
            'makeup': 'MK',
            'skincare': 'SK',
            'cushion': 'CU',
            'tint': 'LI',
            'serum': 'SE',
            'lotion': 'LO',
            'toner': 'TO',
            'cleanser': 'CL',
            'sunscreen': 'SU',
            'mask': 'MA'
        };
        const catKey = (category || '').toLowerCase();
        let catCode = catMap[catKey] || catKey.substring(0, 2).toUpperCase() || 'XX';

        // LINE_SLUG
        let lineSlug = 'GEN';
        if (parentID) {
            lineSlug = parentID.replace('LINE-', '').toUpperCase();
        } else if (productName) {
            lineSlug = productName.split(' ')[0].toUpperCase();
        }

        // NN Counting
        let count = 1;
        if (parentID) {
            count = await Product.countDocuments({ Parent_ID: parentID }) + 1;
        } else if (productName) {
            // Count products that start with the same name logic
            const regex = new RegExp('^' + productName.split(' ')[0], 'i');
            count = await Product.countDocuments({ Name: { $regex: regex } }) + 1;
        } else {
            count = await Product.countDocuments({ Category: category }) + 1;
        }
        const nn = count.toString().padStart(2, '0');

        const suggestedProductID = `PRD-${catCode}-${lineSlug}-${nn}`;
        const suggestedShadeID = parentID ? `${parentID}-01` : null; // Base shade ID

        res.json({ suggestedProductID, suggestedShadeID });
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

// Lab Conversion Helper (D65)
const rgbToLab = (r, g, b) => {
    let r2 = r / 255, g2 = g / 255, b2 = b / 255;
    r2 = r2 > 0.04045 ? Math.pow((r2 + 0.055) / 1.055, 2.4) : r2 / 12.92;
    g2 = g2 > 0.04045 ? Math.pow((g2 + 0.055) / 1.055, 2.4) : g2 / 12.92;
    b2 = b2 > 0.04045 ? Math.pow((b2 + 0.055) / 1.055, 2.4) : b2 / 12.92;

    const x = (r2 * 0.4124 + g2 * 0.3576 + b2 * 0.1805) * 100;
    const y = (r2 * 0.2126 + g2 * 0.7152 + b2 * 0.0722) * 100;
    const z = (r2 * 0.0193 + g2 * 0.1192 + b2 * 0.9505) * 100;

    let x3 = x / 95.047, y3 = y / 100.000, z3 = z / 108.883;
    x3 = x3 > 0.008856 ? Math.pow(x3, 1 / 3) : (7.787 * x3) + (16 / 116);
    y3 = y3 > 0.008856 ? Math.pow(y3, 1 / 3) : (7.787 * y3) + (16 / 116);
    z3 = z3 > 0.008856 ? Math.pow(z3, 1 / 3) : (7.787 * z3) + (16 / 116);

    return {
        L: Number(((116 * y3) - 16).toFixed(1)),
        a: Number((500 * (x3 - y3)).toFixed(1)),
        b: Number((200 * (y3 - z3)).toFixed(1))
    };
};

// Hex to RGB
const hexToRgb = (hex) => {
    let bg = hex.replace('#', '');
    if (bg.length === 3) bg = bg.split('').map(c => c+c).join('');
    return {
        r: parseInt(bg.substring(0, 2), 16),
        g: parseInt(bg.substring(2, 4), 16),
        b: parseInt(bg.substring(4, 6), 16)
    };
}
const rgbToHex = (r, g, b) => '#' + [r, g, b].map(x => {
    const hex = Math.round(x).toString(16);
    return hex.length === 1 ? '0' + hex : hex;
}).join('').toUpperCase();

// 3. POST /api/v1/admin/smart/extract-color
// Requires multer middleware `upload.single('image')`
exports.extractColor = async (req, res) => {
    try {
        if (!req.file) return res.status(400).json({ message: "No image provided" });

        // Resize and get raw buffer
        const { data, info } = await sharp(req.file.buffer)
            .resize(80, 80, { fit: 'cover' })
            .raw()
            .toBuffer({ resolveWithObject: true });

        let rSum = 0, gSum = 0, bSum = 0, count = 0;
        
        // 3 channels (RGB) or 4 (RGBA)
        const channels = info.channels; 
        
        for (let i = 0; i < data.length; i += channels) {
            const a = channels === 4 ? data[i + 3] : 255;
            if (a > 128) { // Only count non-transparent
                rSum += data[i];
                gSum += data[i + 1];
                bSum += data[i + 2];
                count++;
            }
        }

        if (count === 0) return res.status(400).json({ message: "Image is fully transparent" });

        const r = Math.round(rSum / count);
        const g = Math.round(gSum / count);
        const b = Math.round(bSum / count);
        const hex = rgbToHex(r, g, b);
        const lab = rgbToLab(r, g, b);

        res.json({
            hex,
            r, g, b,
            ...lab
        });
    } catch (err) {
        console.error("Color Extract Error:", err);
        res.status(500).json({ message: err.message });
    }
};

// 4. POST /api/v1/admin/smart/extract-color/hex
exports.extractColorHex = async (req, res) => {
    try {
        const { hex } = req.body;
        if (!hex) return res.status(400).json({ message: "Hex code required" });

        const rgb = hexToRgb(hex);
        const lab = rgbToLab(rgb.r, rgb.g, rgb.b);

        res.json({
            hex: hex.toUpperCase(),
            ...rgb,
            ...lab
        });
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};
