const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const User = require('../models/user.model');

async function listAdmins() {
    try {
        await mongoose.connect(process.env.MONGO_URI);
        const admins = await User.find({ role: 'admin' }).select('name email isEmailVerified createdAt');
        
        console.log('\n--- EXISTING ADMIN ACCOUNTS ---');
        if (admins.length === 0) {
            console.log('No admin accounts found.');
        } else {
            admins.forEach(admin => {
                console.log(`- ${admin.name} (${admin.email}) | Verified: ${admin.isEmailVerified} | Created: ${admin.createdAt}`);
            });
        }
        console.log('-------------------------------\n');
        
    } catch (error) {
        console.error(error);
    } finally {
        await mongoose.disconnect();
    }
}

listAdmins();
