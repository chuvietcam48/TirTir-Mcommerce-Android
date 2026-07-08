require('dotenv').config();
const mongoose = require('mongoose');

async function check() {
  await mongoose.connect(process.env.MONGO_URI);
  const Order = require('./models/Order');
  const order = await Order.findOne().sort({ createdAt: -1 }).lean();
  require('fs').writeFileSync('dump.json', JSON.stringify(order, null, 2));
  console.log('Dumped to dump.json');
  process.exit(0);
}

check().catch(console.error);
