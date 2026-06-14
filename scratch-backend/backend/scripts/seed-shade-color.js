const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');

// Load environment variables
dotenv.config({ path: path.join(__dirname, '../.env') });

const Product = require('../models/product.model');

// Predefined list of typical cushion/foundation shade hex codes
const shadeColors = [
    "#F4D8C8", // Very Light
    "#F2D0B6", // Light
    "#EAC3A6", // Light Medium
    "#E1B596", // Medium
    "#D4A47C", // Tan
    "#C5946E", // Medium Deep
    "#B17D56"  // Deep
];

async function seedShadeColor() {
    try {
        const mongoUri = process.env.MONGODB_URI || 'mongodb://localhost:27017/tirtir';
        console.log(`Connecting to MongoDB...`);
        await mongoose.connect(mongoUri);
        console.log('MongoDB connected.');

        // Update cushion and foundation products
        // Note: the category might be case-sensitive, matching regex or in array
        const products = await Product.find({ 
            Category: { $regex: /cushion|foundation/i } 
        });
        
        console.log(`Found ${products.length} products to update.`);

        let updatedCount = 0;
        for (const product of products) {
            if (!product.shade_color_hex) {
                const randomHex = shadeColors[Math.floor(Math.random() * shadeColors.length)];
                product.shade_color_hex = randomHex;
                await product.save({ validateBeforeSave: false });
                console.log(`Updated product ${product.Product_ID} with hex ${randomHex}`);
                updatedCount++;
            } else {
                console.log(`Product ${product.Product_ID} already has shade_color_hex: ${product.shade_color_hex}`);
            }
        }

        console.log(`Migration completed successfully. Updated ${updatedCount} products.`);
        process.exit(0);
    } catch (err) {
        console.error('Migration failed:', err);
        process.exit(1);
    }
}

seedShadeColor();
