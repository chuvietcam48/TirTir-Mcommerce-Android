/**
 * Seed Admin User Script
 * Creates (or updates) the admin account for the Admin Dashboard.
 *
 * Run: node scripts/seed-admin.js
 */

require('dotenv').config({ path: require('path').join(__dirname, '../.env') });
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const User = require('../models/user.model');

const ADMIN = {
    name: 'TirTir Admin',
    email: 'admin@tirtir.com',
    password: 'admin123',
    role: 'admin',
    isEmailVerified: true,   // ← bypass email verification
    isBlocked: false
};

async function seedAdmin() {
    try {
        await mongoose.connect(process.env.MONGO_URI);
        console.log('✅ MongoDB connected');

        const hashedPassword = await bcrypt.hash(ADMIN.password, 10);

        const existing = await User.findOne({ email: ADMIN.email });

        if (existing) {
            // Use updateOne() to bypass the pre-save hook — password is already hashed above
            await User.updateOne(
                { email: ADMIN.email },
                {
                    $set: {
                        name: ADMIN.name,
                        password: hashedPassword, // pre-hashed — no double-hash
                        role: ADMIN.role,
                        isEmailVerified: true,
                        isBlocked: false,
                        refreshToken: undefined,
                        refreshTokenExpire: undefined
                    }
                }
            );
            console.log(`🔄 Updated existing user: ${ADMIN.email} → role=admin, verified=true`);
        } else {
            // For new users, set plain password — pre-save hook will hash it exactly once
            await User.create({
                name: ADMIN.name,
                email: ADMIN.email,
                password: ADMIN.password, // let pre-save hook hash it
                role: ADMIN.role,
                isEmailVerified: true,
                isBlocked: false
            });
            console.log(`🌱 Created new admin user: ${ADMIN.email}`);
        }

        console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        console.log('  ADMIN CREDENTIALS');
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        console.log(`  Email   : ${ADMIN.email}`);
        console.log(`  Password: ${ADMIN.password}`);
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    } catch (err) {
        console.error('❌ Seed failed:', err.message);
    } finally {
        await mongoose.disconnect();
        console.log('🔌 Disconnected from MongoDB');
    }
}

seedAdmin();
