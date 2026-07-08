require('dotenv').config();
const mongoose = require('mongoose');

// Kết nối với schema lỏng (strict: false) để bypass mọi validation của Mongoose
const UserSchema = new mongoose.Schema({}, { strict: false });
const User = mongoose.model('UserTemp', UserSchema, 'users');

async function run() {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log('Connected to DB');

    const userId = '6982b531907ce740387af9ef';
    const token = 'fm7Jm5ciQ4S4ukHuZWNDLO:APA91bG_yoO2aWpIYL2pS0oeR6KUTDN8TKUxMn-NWN4-Z-0MKmzecuWBg8C8DtaiYPu83MnNfa6kwHEEsuRP0Qd3uoP46rCMRq1zypZvxT0u10gPLYbzeyM';

    const result = await User.updateOne(
      { _id: new mongoose.Types.ObjectId(userId) },
      { 
        $set: { 
          fcmTokens: [{
            token: token,
            platform: 'android',
            active: true
          }] 
        } 
      }
    );

    console.log('Update result:', result);
    console.log('✅ Đã chèn ép Token vào MongoDB thành công!');
    process.exit(0);
  } catch (error) {
    console.error(error);
    process.exit(1);
  }
}

run();
