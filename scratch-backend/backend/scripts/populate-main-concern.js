/**
 * Migration: Populate Main_Concern field for all products based on Name + Description analysis.
 * Run once: node scripts/populate-main-concern.js
 */
const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const Product = require('../models/product.model');

// Keyword → concern mapping for TirTir products
const CONCERN_RULES = [
    { keywords: ['brightening', 'bright', 'glow', 'luminous', 'radiant'], concern: 'Pigmentation' },
    { keywords: ['hydra', 'hydro', 'moisture', 'hyaluronic', 'water'], concern: 'Dryness' },
    { keywords: ['acne', 'blemish', 'pore', 'oil control', 'matte'], concern: 'Acne' },
    { keywords: ['anti-ag', 'wrinkle', 'firm', 'collagen', 'elasticity', 'lifting'], concern: 'Wrinkles' },
    { keywords: ['calm', 'sooth', 'sensitive', 'cica', 'centella', 'barrier'], concern: 'Redness' },
    { keywords: ['sun', 'spf', 'uv', 'protection'], concern: 'Sun Protection' },
    { keywords: ['eye', 'dark circle', 'under-eye'], concern: 'Dark Circles' },
    { keywords: ['tone up', 'even', 'pigment', 'spot'], concern: 'Pigmentation' },
    { keywords: ['ceramic', 'repair', 'restore'], concern: 'Dryness' },
    { keywords: ['oil', 'jojoba', 'nourish'], concern: 'Dryness' },
    { keywords: ['exfoli', 'enzyme', 'peel'], concern: 'Texture' },
];

async function migrate() {
    await mongoose.connect(process.env.MONGO_URI);
    console.log('Connected to MongoDB');

    const products = await Product.find({});
    let updated = 0;

    for (const product of products) {
        // Skip if already has Main_Concern
        if (product.Main_Concern) continue;

        const text = `${product.Name || ''} ${product.Description_Short || ''} ${product.Full_Description || ''}`.toLowerCase();
        
        let bestConcern = null;
        for (const rule of CONCERN_RULES) {
            if (rule.keywords.some(kw => text.includes(kw))) {
                bestConcern = rule.concern;
                break;
            }
        }

        // Default concern based on category
        if (!bestConcern) {
            const cat = (product.Category || '').toLowerCase();
            if (cat === 'cushion') bestConcern = 'Coverage';
            else if (cat === 'skincare') bestConcern = 'General Care';
            else if (cat === 'primer') bestConcern = 'Base Makeup';
            else bestConcern = 'General Care';
        }

        product.Main_Concern = bestConcern;
        await product.save();
        updated++;
        console.log(`  ✅ ${product.Name} → ${bestConcern}`);
    }

    console.log(`\nDone! Updated ${updated}/${products.length} products`);
    await mongoose.disconnect();
}

migrate().catch(err => {
    console.error('Migration failed:', err);
    process.exit(1);
});
