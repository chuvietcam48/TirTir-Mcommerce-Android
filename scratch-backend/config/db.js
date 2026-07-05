const mongoose = require('mongoose');

const connectDB = async () => {
  const conn = await mongoose.connect(process.env.MONGO_URI);
  console.log(`MongoDB connected: ${conn.connection.host}`);
  try {
    await mongoose.connection.collection('vouchers').dropIndex('code_1');
    console.log('Successfully dropped code_1 unique index on vouchers collection');
  } catch (err) {
    // Index may not exist, ignore error
  }
};

module.exports = connectDB;
