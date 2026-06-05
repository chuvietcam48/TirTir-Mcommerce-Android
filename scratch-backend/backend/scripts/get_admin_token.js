const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const User = require('../models/user.model');

const ADMIN_EMAIL = 'admin@tirtir.com';

async function getAdminToken() {
    try {
        if (!process.env.JWT_SECRET) {
            console.error('FATAL: JWT_SECRET is not defined in .env');
            process.exit(1);
        }

        console.log('Connecting to MongoDB...');
        await mongoose.connect(process.env.MONGO_URI);
        
        const adminUser = await User.findOne({ email: ADMIN_EMAIL });

        if (!adminUser) {
            console.error(`Admin user ${ADMIN_EMAIL} not found! Please run ensure_admin.js first.`);
        } else {
            const token = jwt.sign(
                { id: adminUser._id, role: adminUser.role },
                process.env.JWT_SECRET,
                { expiresIn: '365d' } // Changed to 365 days for dev convenience
            );

            console.log('\n==================================================');
            console.log('🔑 ADMIN TOKEN GENERATED SUCCESSFULLY');
            console.log('==================================================');
            console.log('User:', adminUser.email);
            console.log('Role:', adminUser.role);
            console.log('ID:  ', adminUser._id);
            console.log('--------------------------------------------------');
            console.log('Token:');
            console.log(token);
            console.log('==================================================\n');
        }

    } catch (error) {
        console.error('Error generating token:', error);
    } finally {
        await mongoose.disconnect();
    }
}

getAdminToken();
