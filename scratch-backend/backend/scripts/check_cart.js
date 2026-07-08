const mongoose = require('mongoose');
const Cart = require('../../models/Cart');
const Product = require('../../models/Product');
const User = require('../../models/User');

require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });

async function checkCart() {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log('Connected to MongoDB');

    const users = await User.find({ email: 'cee.m48@gmail.com' });
    if (users.length === 0) {
      console.log('User not found');
      process.exit(1);
    }

    const userId = users[0]._id;
    console.log('User ID:', userId);

    const cart = await Cart.findOne({ userId });
    console.log('Cart:', JSON.stringify(cart, null, 2));

    if (cart && cart.items.length > 0) {
      const productIds = cart.items.map((i) => i.productId);
      console.log('Product IDs in cart:', productIds);

      const products = await Product.find({ _id: { $in: productIds } }).lean();
      console.log('Found Products:', products.map(p => ({ _id: p._id, name: p.Name })));
      
      const productMap = {};
      products.forEach((p) => { productMap[String(p._id)] = p; });

      cart.items.forEach(cartItem => {
        const p = productMap[cartItem.productId];
        console.log(`Cart Item: ${cartItem.productId} -> Found: ${p ? p.Name : 'UNKNOWN'}`);
      });
    }
  } catch (error) {
    console.error('Error:', error);
  } finally {
    process.exit(0);
  }
}

checkCart();
