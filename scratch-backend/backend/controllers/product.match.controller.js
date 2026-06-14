const Product = require('../models/product.model');

function hexToRgb(hex) {
    const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
    hex = hex.replace(shorthandRegex, function(m, r, g, b) {
        return r + r + g + g + b + b;
    });

    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
    } : null;
}

function rgbToLab(rgb) {
    let r = rgb.r / 255, g = rgb.g / 255, b = rgb.b / 255, x, y, z;
    
    r = (r > 0.04045) ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
    g = (g > 0.04045) ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
    b = (b > 0.04045) ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;

    x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
    y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.00000;
    z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;

    x = (x > 0.008856) ? Math.pow(x, 1/3) : (7.787 * x) + 16/116;
    y = (y > 0.008856) ? Math.pow(y, 1/3) : (7.787 * y) + 16/116;
    z = (z > 0.008856) ? Math.pow(z, 1/3) : (7.787 * z) + 16/116;

    return {
        l: (116 * y) - 16,
        a: 500 * (x - y),
        b: 200 * (y - z)
    };
}

function calculateDeltaE(lab1, lab2) {
    return Math.sqrt(
        Math.pow(lab1.l - lab2.l, 2) +
        Math.pow(lab1.a - lab2.a, 2) +
        Math.pow(lab1.b - lab2.b, 2)
    );
}

/**
 * @desc    Find top 5 matching cushions/foundations based on skin tone hex
 * @route   GET /api/v1/products/cushion-match?skin_tone_hex=#D4A47C
 * @access  Public
 */
exports.getCushionMatch = async (req, res, next) => {
    try {
        const { skin_tone_hex } = req.query;
        if (!skin_tone_hex) {
            return res.status(400).json({ success: false, message: "Vui lòng cung cấp skin_tone_hex" });
        }

        const userRgb = hexToRgb(skin_tone_hex);
        if (!userRgb) {
            return res.status(400).json({ success: false, message: "Mã hex không hợp lệ" });
        }
        const userLab = rgbToLab(userRgb);

        const products = await Product.find({ 
            Category: { $regex: /cushion|foundation/i },
            shade_color_hex: { $ne: null }
        }).select('Product_ID Name Price Thumbnail_Images Category shade_color_hex');

        if (!products || products.length === 0) {
             return res.status(200).json({ success: true, data: [] });
        }

        const matchedProducts = products.map(product => {
            const prodRgb = hexToRgb(product.shade_color_hex);
            let deltaE = 999;
            if (prodRgb) {
                const prodLab = rgbToLab(prodRgb);
                deltaE = calculateDeltaE(userLab, prodLab);
            }
            
            let label = "Acceptable";
            if (deltaE < 2) label = "Perfect Match";
            else if (deltaE <= 10) label = "Good Match";

            return {
                ...product.toObject(),
                deltaE: parseFloat(deltaE.toFixed(2)),
                match_label: label
            };
        });

        // Sort ascending by deltaE
        matchedProducts.sort((a, b) => a.deltaE - b.deltaE);

        // Get top 5
        const top5 = matchedProducts.slice(0, 5);

        res.status(200).json({
            success: true,
            count: top5.length,
            data: top5
        });
    } catch (err) {
        next(err);
    }
};
