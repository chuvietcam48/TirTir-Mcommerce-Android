const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');
const fs = require('fs');
const { parse } = require('csv-parse/sync');

dotenv.config({ path: path.join(__dirname, '../../.env') });
const Product = require('../models/product.model');

async function seedCatalog() {
    try {
        const mongoUri = process.env.MONGO_URI || 'mongodb://localhost:27017/tirtir';
        
        console.log('Connecting to MongoDB...');
        await mongoose.connect(mongoUri);
        console.log('MongoDB connected.');

        const csvPath = path.join(__dirname, '../chatbot/chatbot_products.csv');
        if (!fs.existsSync(csvPath)) {
            console.error('CSV file not found at:', csvPath);
            process.exit(1);
        }

        const fileContent = fs.readFileSync(csvPath, 'utf-8');
        const records = parse(fileContent, {
            columns: true,
            skip_empty_lines: true
        });

        console.log(`Found ${records.length} products in CSV. Seeding...`);

        let inserted = 0;
        let updated = 0;

        for (const record of records) {
            // Parse images safely
            let descriptionImages = [];
            let galleryImages = [];
            try {
                if (record.Description_Images && record.Description_Images !== '[]') {
                    descriptionImages = JSON.parse(record.Description_Images);
                }
            } catch (e) {}

            try {
                if (record.Gallery_Images && record.Gallery_Images !== '[]') {
                    galleryImages = JSON.parse(record.Gallery_Images);
                }
            } catch (e) {}

            // Calculate Is_Skincare boolean properly
            const isSkincare = record.Is_Skincare === 'TRUE' || record.Is_Skincare === 'true';

            const updateData = {
                Parent_ID: record.Parent_ID,
                Category: record.Category,
                Category_Slug: record.Category_Slug,
                Name: record.Name,
                slug: record.Product_Slug || undefined,
                Price: parseFloat(record.Price) || 0,
                Volume_Size: record.Volume_Size,
                Is_Skincare: isSkincare,
                Skin_Type_Target: record.Skin_Type_Target,
                Main_Concern: record.Main_Concern,
                Description_Short: record.Description_Short,
                How_To_Use: record.How_To_Use,
                Status: record.Status,
                Stock_Quantity: parseInt(record.Stock_Quantity) || 0,
                Full_Description: record.Full_Description,
                Thumbnail_Images: record.Thumbnail_Images,
                Gallery_Images: galleryImages,
                Description_Images: descriptionImages
            };

            const result = await Product.updateOne(
                { Product_ID: record.Product_ID },
                { $set: updateData },
                { upsert: true }
            );

            if (result.upsertedCount > 0) {
                inserted++;
            } else if (result.modifiedCount > 0) {
                updated++;
            }
        }

        console.log(`Catalog seeded successfully! Inserted: ${inserted}, Updated: ${updated}`);
        process.exit(0);
    } catch (error) {
        console.error('Error seeding catalog:', error);
        process.exit(1);
    }
}

seedCatalog();
