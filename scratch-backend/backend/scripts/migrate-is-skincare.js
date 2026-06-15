const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');

dotenv.config({ path: path.join(__dirname, '../../.env') });
const Product = require('../models/product.model');

async function migrateIsSkincare() {
    try {
        const mongoUri = process.env.MONGO_URI || 'mongodb://localhost:27017/tirtir';
        
        console.log('Connecting to MongoDB...');
        await mongoose.connect(mongoUri);
        console.log('MongoDB connected.');

        // Find products where Is_Skincare is stored as a String instead of Boolean
        const products = await Product.find({ Is_Skincare: { $type: "string" } });
        
        console.log(`Found ${products.length} products with incorrect Is_Skincare type.`);

        let updated = 0;
        for (const product of products) {
            // @ts-ignore - explicitly override the type
            const stringVal = product.Is_Skincare;
            product.Is_Skincare = stringVal === 'TRUE' || stringVal === 'true';
            await product.save({ validateBeforeSave: false });
            updated++;
        }

        console.log(`Migration successful! Fixed ${updated} records.`);
        process.exit(0);
    } catch (error) {
        console.error('Migration failed:', error);
        process.exit(1);
    }
}

migrateIsSkincare();
