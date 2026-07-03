const mongoose = require('mongoose');
const Product = require('./models/Product');

mongoose.connect('mongodb://127.0.0.1:27017/Tirtir', { useNewUrlParser: true, useUnifiedTopology: true })
  .then(async () => {
    console.log("Connected to MongoDB. Updating Stock_Quantity...");
    const result = await Product.updateMany(
      { $or: [{ Stock_Quantity: 0 }, { Stock_Quantity: { $exists: false } }, { Stock_Quantity: null }] },
      { $set: { Stock_Quantity: 100 } }
    );
    console.log("Updated products:", result.modifiedCount);
    process.exit(0);
  })
  .catch(err => {
    console.error("Error:", err);
    process.exit(1);
  });
