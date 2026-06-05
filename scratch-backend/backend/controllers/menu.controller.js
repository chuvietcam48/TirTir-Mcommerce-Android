const Product = require('../models/product.model');

/**
 * Menu Configuration
 * Keys correspond to database search terms for categorization.
 */
const CATEGORY_RULES = {
    Makeup: {
        'Face': ['cushion', 'foundation', 'primer', 'setting-spray', 'spray', 'fixer', 'concealer', 'base', 'powder', 'blush', 'contour', 'highlighter', 'shading'],
        'Lip': ['lip', 'tint', 'balm', 'gloss', 'stick', 'oil', 'plumper', 'care'],
        'Eye': ['mascara', 'eyeliner', 'shadow', 'brow', 'lash', 'pencil']
    },
    Skincare: {
        'Cleanse & Tone': ['cleanser', 'toner', 'pad', 'wash', 'foam', 'water', 'remover', 'milk', 'soap', 'mist'],
        'Treatments': ['serum', 'ampoule', 'essence', 'eye-cream', 'mask', 'treatment', 'spot', 'patch', 'facial-oil', 'exfoliator', 'peel'],
        'Moisturize & Sun': ['cream', 'lotion', 'moisturizer', 'sunscreen', 'sun', 'gel', 'emulsion', 'gift-set', 'kit', 'set', 'balm']
    }
};

const formatLabel = (slug) => {
    if (!slug) return '';
    return slug.split('-')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
};

exports.getMenus = async (req, res) => {
    try {
        const products = await Product.find({}).select('Product_ID Category_Slug Name Is_Skincare').lean();

        const buckets = {
            Makeup: { Face: new Set(), Lip: new Set(), Eye: new Set(), Other: new Set() },
            Skincare: { 'Cleanse & Tone': new Set(), Treatments: new Set(), 'Moisturize & Sun': new Set(), Other: new Set() }
        };

        const processedSlugs = new Set();

        products.forEach(p => {
            const slug = p.Category_Slug;
            if (!slug || processedSlugs.has(slug)) return;
            processedSlugs.add(slug);

            const pid = (p.Product_ID || '').toUpperCase();
            const searchText = (slug + ' ' + (p.Name || '')).toLowerCase();

            // 1. Determine Main Category (ID-Based)
            let isSkincare = null;

            if (pid.startsWith('MK')) {
                isSkincare = false;
            } else if (pid.startsWith('SK') || pid.startsWith('SC')) {
                isSkincare = true;
            } else {
                // Fallback for irregular IDs
                isSkincare = (p.Is_Skincare === true || String(p.Is_Skincare).toUpperCase() === 'TRUE');
            }

            // 2. Assign to Column (Keyword-Based)
            let targetCol = 'Other';
            const rules = isSkincare ? CATEGORY_RULES.Skincare : CATEGORY_RULES.Makeup;
            const targetBucket = isSkincare ? buckets.Skincare : buckets.Makeup;

            for (const [col, keywords] of Object.entries(rules)) {
                if (keywords.some(k => searchText.includes(k))) {
                    targetCol = col;
                    break;
                }
            }

            targetBucket[targetCol].add(slug);
        });

        // 3. Assemble Response
        const buildSection = (bucketMap, isSkin) => {
            return Object.entries(bucketMap)
                .map(([label, slugSet]) => ({
                    label,
                    routerLink: '/shop',
                    queryParams: { isSkincare: isSkin },
                    children: Array.from(slugSet).sort().map(slug => ({
                        label: formatLabel(slug),
                        routerLink: '/shop',
                        queryParams: { isSkincare: isSkin, categorySlug: slug }
                    }))
                }))
                .filter(col => col.children.length > 0);
        };

        const menuTree = [
            { label: 'Shop All', routerLink: '/shop', _order: 1 },
            {
                label: 'Makeup',
                routerLink: '/shop',
                queryParams: { isSkincare: false },
                children: buildSection(buckets.Makeup, false),
                _order: 2
            },
            {
                label: 'Skincare',
                routerLink: '/shop',
                queryParams: { isSkincare: true },
                children: buildSection(buckets.Skincare, true),
                _order: 3
            },
            { label: 'Virtual Services', routerLink: '/virtual-services', _order: 4 },
            { label: 'Contact', routerLink: '/contact', _order: 5 },
            { label: 'About', routerLink: '/about', _order: 6 }
        ];

        res.json(menuTree);

    } catch (err) {
        console.error("Auto Menu Error:", err);
        res.status(500).json({ message: "Failed to generate menu" });
    }
};