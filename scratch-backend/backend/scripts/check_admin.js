const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const User = require('../models/user.model');

async function checkAdmin() {
    try {
        console.log('Connecting to MongoDB...');
        await mongoose.connect(process.env.MONGO_URI);
        console.log('Connected.\n');

        const admin = await User.findOne({ email: 'admin@tirtir.com' });

        if (admin) {
            console.log('=== ADMIN USER DETAILS ===');
            console.log('Email:', admin.email);
            console.log('Name:', admin.name);
            console.log('Role:', admin.role);
            console.log('isEmailVerified:', admin.isEmailVerified);
            console.log('emailVerificationToken:', admin.emailVerificationToken);
            console.log('isBlocked:', admin.isBlocked);
            console.log('Created:', admin.createdAt);
            console.log('Updated:', admin.updatedAt);
            console.log('========================\n');
        } else {
            console.log('Admin user not found!');
        }

    } catch (error) {
        console.error('Error:', error);
    } finally {
        await mongoose.disconnect();
        console.log('Disconnected from DB.');
    }
}

checkAdmin();
