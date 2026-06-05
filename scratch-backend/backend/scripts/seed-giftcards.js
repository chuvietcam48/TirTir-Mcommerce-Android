const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');

// Load env vars
dotenv.config({ path: path.join(__dirname, '../.env') });

const Product = require('../models/product.model');

const MONGO_URI = process.env.MONGO_URI;

if (!MONGO_URI) {
    console.error('MONGO_URI is missing in .env');
    process.exit(1);
}

const giftCards = [
    {
        Product_ID: 'tirtir-gift-card-10',
        Name: 'TIRTIR GIFT CARD',
        Price: 10,
        Category: 'Gift Card',
        Thumbnail_Images: 'assets/images/additional/giftcard/GifCardMain.jpeg',
        Description_Short: 'Give the gift of choice.',
        Full_Description: 'Shopping for someone else but not sure what to give them? Give them the gift of choice with a TIRTIR gift card.',
        Stock_Quantity: 9999,
        slug: 'tirtir-gift-card-10'
    },
    {
        Product_ID: 'tirtir-gift-card-25',
        Name: 'TIRTIR GIFT CARD',
        Price: 25,
        Category: 'Gift Card',
        Thumbnail_Images: 'assets/images/additional/giftcard/GifCardMain.jpeg',
        Description_Short: 'Give the gift of choice.',
        Full_Description: 'Shopping for someone else but not sure what to give them? Give them the gift of choice with a TIRTIR gift card.',
        Stock_Quantity: 9999,
        slug: 'tirtir-gift-card-25'
    },
    {
        Product_ID: 'tirtir-gift-card-50',
        Name: 'TIRTIR GIFT CARD',
        Price: 50,
        Category: 'Gift Card',
        Thumbnail_Images: 'assets/images/additional/giftcard/GifCardMain.jpeg',
        Description_Short: 'Give the gift of choice.',
        Full_Description: 'Shopping for someone else but not sure what to give them? Give them the gift of choice with a TIRTIR gift card.',
        Stock_Quantity: 9999,
        slug: 'tirtir-gift-card-50'
    },
    {
        Product_ID: 'tirtir-gift-card-100',
        Name: 'TIRTIR GIFT CARD',
        Price: 100,
        Category: 'Gift Card',
        Thumbnail_Images: 'assets/images/additional/giftcard/GifCardMain.jpeg',
        Description_Short: 'Give the gift of choice.',
        Full_Description: 'Shopping for someone else but not sure what to give them? Give them the gift of choice with a TIRTIR gift card.',
        Stock_Quantity: 9999,
        slug: 'tirtir-gift-card-100'
    }
];

const seed = async () => {
    try {
        await mongoose.connect(MONGO_URI);
        console.log('MongoDB Connected');

        for (const card of giftCards) {
            await Product.findOneAndUpdate(
                { Product_ID: card.Product_ID },
                card,
                { upsert: true, new: true }
            );
            console.log(`Seeded: ${card.Product_ID} - $${card.Price}`);
        }

        console.log('Done');
        process.exit(0);
    } catch (err) {
        console.error(err);
        process.exit(1);
    }
};

seed();
