require('dotenv').config();
const mongoose = require('mongoose');
const User = require('./models/User');
const Product = require('./models/Product');
const Review = require('./models/Review');

const reviewTitles = [
    "Amazing product!",
    "Highly recommended",
    "Works well for my skin",
    "Not bad, but could be better",
    "Love the texture",
    "My holy grail!",
    "Will definitely repurchase",
    "Good value for money",
    "A bit pricey but worth it",
    "Perfect for daily use",
    "Absolutely love it!",
    "Changed my skincare game"
];

const reviewComments = [
    "I have been using this for a few weeks now and I can really see the difference. My skin feels so much smoother and hydrated. Definitely worth the hype!",
    "It's okay, but I didn't see any drastic changes. I might give it a bit more time. The packaging is really nice though.",
    "This is simply amazing! I couldn't believe how well it worked right from the first use. It absorbs quickly and leaves no sticky residue.",
    "Good product, but the scent is a bit too strong for me. If you are sensitive to fragrance, you might want to patch test first.",
    "Absolutely in love with this! It gives me that perfect glass skin look without feeling heavy. Highly recommend it to everyone.",
    "It caused a slight breakout when I first started using it, but after a week my skin got used to it and now it's clear and glowing.",
    "I bought this based on a recommendation and I do not regret it. Very high quality and feels luxurious on the skin.",
    "The texture is so silky and it blends in effortlessly. It sits beautifully under my makeup all day.",
    "It does what it claims, but I think there are cheaper alternatives out there that do the same thing.",
    "This is my third time repurchasing this! It's a staple in my routine now. I can't go a day without it."
];

const connectDB = async () => {
    try {
        await mongoose.connect(process.env.MONGO_URI, {
            useNewUrlParser: true,
            useUnifiedTopology: true
        });
        console.log('MongoDB Connected for Seeding Reviews');
    } catch (err) {
        console.error('Database connection error:', err);
        process.exit(1);
    }
};

const generateReviews = async () => {
    try {
        // Get all products and users
        const products = await Product.find({});
        const users = await User.find({});
        
        if (users.length === 0) {
            console.log('No users found. Please seed users first.');
            process.exit(1);
        }
        
        if (products.length === 0) {
            console.log('No products found. Please seed products first.');
            process.exit(1);
        }

        console.log(`Found ${products.length} products and ${users.length} users. Generating reviews...`);

        // Clear existing reviews
        await Review.deleteMany({});
        console.log('Cleared existing reviews.');

        let totalReviewsCreated = 0;

        for (const product of products) {
            const numReviews = Math.floor(Math.random() * (80 - 20 + 1)) + 20; // Generate between 20 and 80 reviews per product
            
            let totalRating = 0;
            const reviewDocs = [];

            for (let i = 0; i < numReviews; i++) {
                const randomUser = users[Math.floor(Math.random() * users.length)];
                
                // Bias ratings towards 4 and 5
                const rand = Math.random();
                let rating = 5;
                if (rand > 0.8) rating = 4;
                else if (rand > 0.95) rating = 3;
                else if (rand > 0.98) rating = 2;
                else if (rand > 0.99) rating = 1;

                const title = reviewTitles[Math.floor(Math.random() * reviewTitles.length)];
                const comment = reviewComments[Math.floor(Math.random() * reviewComments.length)];
                
                // Add some random date in the past 6 months
                const dateOffset = (24 * 60 * 60 * 1000) * (Math.floor(Math.random() * 180));
                const randomDate = new Date(Date.now() - dateOffset);

                totalRating += rating;
                
                reviewDocs.push({
                    user: randomUser._id,
                    productId: product.Product_ID,
                    rating,
                    title,
                    comment,
                    createdAt: randomDate,
                    updatedAt: randomDate
                });
            }

            await Review.insertMany(reviewDocs);
            
            // Update product average
            const avgRating = totalRating / numReviews;
            const roundedAvg = Math.round(avgRating * 10) / 10;

            await Product.findByIdAndUpdate(product._id, {
                Rating_Average: roundedAvg,
                Rating_Count: numReviews
            });
            
            totalReviewsCreated += numReviews;
            console.log(`Product ${product.Product_ID} - Created ${numReviews} reviews (Avg: ${roundedAvg})`);
        }

        console.log(`Successfully seeded ${totalReviewsCreated} total reviews!`);
        process.exit();

    } catch (err) {
        console.error('Seeding error:', err);
        process.exit(1);
    }
};

connectDB().then(() => {
    generateReviews();
});
