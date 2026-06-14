const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');

dotenv.config({ path: path.join(__dirname, '../.env') });
const Product = require('../models/product.model');

// TirTir Milk Blur Tint shades
const LIPSTICK_SHADES = [
    { shade_name: '01 Red Drop', shade_color_hex: '#DC143C' },
    { shade_name: '02 Peach Mousse', shade_color_hex: '#FFDAB9' },
    { shade_name: '03 Rosy Nude', shade_color_hex: '#BC8F8F' },
    { shade_name: '04 Chilli Red', shade_color_hex: '#E32227' },
    { shade_name: '05 Brick Brown', shade_color_hex: '#8B4513' },
    { shade_name: '06 Plum Berry', shade_color_hex: '#8E4585' }
];

async function seedLipstickShades() {
    try {
        const mongoUri = process.env.MONGODB_URI || 'mongodb://localhost:27017/tirtir';
        await mongoose.connect(mongoUri);
        console.log('MongoDB connected.');

        // Find products
        const products = await Product.find({ 
            Category: { $regex: /lipstick|lip-tint|tint/i } 
        });
        
        console.log(`Found ${products.length} lipstick/lip-tint products to update.`);

        let updatedCount = 0;
        for (const product of products) {
            const randomShade = LIPSTICK_SHADES[Math.floor(Math.random() * LIPSTICK_SHADES.length)];
            product.shade_name = randomShade.shade_name;
            product.shade_color_hex = randomShade.shade_color_hex;
            
            await product.save({ validateBeforeSave: false });
            console.log(`Updated product ${product.Product_ID} with shade: ${product.shade_name} (${product.shade_color_hex})`);
            updatedCount++;
        }

        console.log(`Migration completed. Updated ${updatedCount} products.`);
        process.exit(0);
    } catch (err) {
        console.error('Migration failed:', err);
        process.exit(1);
    }
}

seedLipstickShades();
