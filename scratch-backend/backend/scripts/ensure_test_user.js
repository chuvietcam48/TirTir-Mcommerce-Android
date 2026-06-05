
require('dotenv').config(); // Use default path (cwd) which is backend/ when running from backend/
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const User = require('../models/user.model'); // Adjust path

const TEST_EMAIL = 'test@example.com';
const TEST_PASSWORD = 'password123'; // Standard test password
const TEST_NAME = 'Test User';

async function ensureUser() {
    try {
        if (!process.env.MONGO_URI) {
            console.error('❌ MONGO_URI is missing in .env');
            process.exit(1);
        }

        await mongoose.connect(process.env.MONGO_URI);
        console.log('✅ Connected to MongoDB');

        let user = await User.findOne({ email: TEST_EMAIL });

        if (user) {
            console.log(`ℹ️ User ${TEST_EMAIL} already exists.`);
            // Update password - DO NOT HASH MANUALLY (Model pre-save hook does it)
            user.password = TEST_PASSWORD;
            user.isEmailVerified = true;
            await user.save();
            console.log(`✅ Reset password for ${TEST_EMAIL} to '${TEST_PASSWORD}'`);
        } else {
            console.log(`ℹ️ User ${TEST_EMAIL} does not exist. Creating...`);
            // Create user - DO NOT HASH MANUALLY (Model pre-save hook does it)
            user = await User.create({
                name: TEST_NAME,
                email: TEST_EMAIL,
                password: TEST_PASSWORD, // Plain text, model will hash
                isEmailVerified: true,
                role: 'user'
            });
            console.log(`✅ Created new user: ${TEST_EMAIL} / ${TEST_PASSWORD}`);
        }

        console.log('\n=============================================');
        console.log('USER CREDENTIALS FOR POSTMAN:');
        console.log(`Email:    ${TEST_EMAIL}`);
        console.log(`Password: ${TEST_PASSWORD}`);
        console.log('=============================================');

    } catch (error) {
        console.error('❌ Error:', error);
    } finally {
        await mongoose.disconnect();
        console.log('Disconnected DB');
    }
}

ensureUser();
