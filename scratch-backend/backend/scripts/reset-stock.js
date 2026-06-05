const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');

// Load env
dotenv.config();

const MONGO_URI = process.env.MONGO_URI;

if (!MONGO_URI) {
    console.error('MONGO_URI is not defined in .env');
    process.exit(1);
}

// Define Schemas (Minimal for update)
const ProductSchema = new mongoose.Schema({
    Stock_Quantity: Number,
    Stock_Reserved: Number,
    Product_ID: String
});
const ShadeSchema = new mongoose.Schema({
    Stock_Quantity: Number,
    Product_ID: String
});

const Product = mongoose.model('Product', ProductSchema);
const Shade = mongoose.model('Shade', ShadeSchema);

async function resetStock() {
    try {
        await mongoose.connect(MONGO_URI);
        console.log('Connected to MongoDB');

        // 1. Reset all Shades to 100 stock
        console.log('Resetting all Shades to 100 stock...');
        const shadesResult = await Shade.updateMany({}, { $set: { Stock_Quantity: 100 } });
        console.log(`Updated ${shadesResult.modifiedCount} shades.`);

        // 2. Reset all Products
        console.log('Resetting all Products reserved stock...');
        await Product.updateMany({}, { $inc: { Stock_Reserved: 0 } }); // Just to make sure we don't throw error if not exists

        // 3. For each Product, calculate sum of its Shades
        const products = await Product.find({});
        for (const product of products) {
            const shades = await Shade.find({ Product_ID: product.Product_ID });
            
            if (shades.length > 0) {
                // If product has shades, sum them up (should be shades.length * 100)
                const totalStock = shades.reduce((sum, s) => sum + s.Stock_Quantity, 0);
                product.Stock_Quantity = totalStock;
            } else {
                // If no shades, just set to 100
                product.Stock_Quantity = 100;
            }
            product.Stock_Reserved = 0;
            await product.save();
            console.log(`Updated Product ${product.Product_ID}: Stock_Quantity = ${product.Stock_Quantity}`);
        }

        console.log('Stock reset complete!');
        process.exit(0);
    } catch (err) {
        console.error('Error resetting stock:', err);
        process.exit(1);
    }
}

resetStock();
