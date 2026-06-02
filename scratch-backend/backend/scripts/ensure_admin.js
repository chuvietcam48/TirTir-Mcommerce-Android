const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const User = require('../models/user.model');

const ADMIN_EMAIL = 'admin@tirtir.com';
const ADMIN_PASSWORD = 'admin123'; // Simple password for dev
const ADMIN_NAME = 'Super Admin';

async function ensureAdmin() {
    try {
        console.log('Connecting to MongoDB...');
        await mongoose.connect(process.env.MONGO_URI);
        console.log('Connected.');

        const existingAdmin = await User.findOne({ email: ADMIN_EMAIL });

        if (existingAdmin) {
            console.log(`Admin user ${ADMIN_EMAIL} already exists.`);
            // Optional: Reset password if needed, but for now just report existence
            // To be safe for frontend team, let's update the password to be sure it's 'admin123'
            existingAdmin.password = ADMIN_PASSWORD; // Will be hashed by pre-save hook
            existingAdmin.role = 'admin';
            existingAdmin.isEmailVerified = true; // Mark as verified
            existingAdmin.emailVerificationToken = null; // Clear verification token
            await existingAdmin.save();
            console.log(`Updated password for ${ADMIN_EMAIL} to '${ADMIN_PASSWORD}' and marked as verified`);
        } else {
            console.log(`Creating new admin user ${ADMIN_EMAIL}...`);
            const newAdmin = new User({
                name: ADMIN_NAME,
                email: ADMIN_EMAIL,
                password: ADMIN_PASSWORD,
                role: 'admin',
                isEmailVerified: true, // Admin is pre-verified
                emailVerificationToken: null // No verification needed
            });
            await newAdmin.save();
            console.log(`Created admin user: ${ADMIN_EMAIL}`);
        }

        console.log('\n==================================================');
        console.log('ADMIN CREDENTIALS FOR FRONTEND TEAM:');
        console.log(`Email:    ${ADMIN_EMAIL}`);
        console.log(`Password: ${ADMIN_PASSWORD}`);
        console.log('==================================================\n');

    } catch (error) {
        console.error('Error ensuring admin user:', error);
    } finally {
        await mongoose.disconnect();
        console.log('Disconnected from DB.');
    }
}

ensureAdmin();
