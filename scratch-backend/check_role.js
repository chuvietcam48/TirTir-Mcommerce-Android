const mongoose = require('mongoose');
const User = require('./models/User');
require('dotenv').config();

mongoose.connect(process.env.MONGO_URI).then(async () => {
    const user = await User.findOne({ email: 'cee.m48@gmail.com' });
    console.log(user);
    process.exit(0);
});
