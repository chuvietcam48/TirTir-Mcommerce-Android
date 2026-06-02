const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const API_URL = 'http://localhost:5001/api';
const ADMIN_EMAIL = 'stock_test_admin@tirtir.com';
const ADMIN_PASS = 'admin123';
const PRODUCT_CODE = 'TEST-STOCK-001';

async function setupData() {
  await mongoose.connect(process.env.MONGO_URI);
  const Product = require('../models/product.model');
  const StockHistory = require('../models/stock.history.model');
  const User = require('../models/user.model');
  const Cart = require('../models/cart.model');

  // Reset User
  await User.deleteOne({ email: ADMIN_EMAIL });
  const admin = new User({ name: 'Stock Admin', email: ADMIN_EMAIL, password: ADMIN_PASS, role: 'admin', isEmailVerified: true });
  await admin.save();

  // Reset Product
  await Product.deleteOne({ Product_ID: PRODUCT_CODE });
  const product = new Product({
      Product_ID: PRODUCT_CODE,
      Name: 'Test Stock Product',
      Price: 100,
      Category: 'Test',
      Stock_Quantity: 10,
      Thumbnail_Images: 'test.jpg'
  });
  const savedProduct = await product.save();
  
  // Clear History
  await StockHistory.deleteMany({ product: savedProduct._id });
  
  // Clear Cart
  await Cart.deleteOne({ user: admin._id });

  await mongoose.connection.close();
  return { userId: admin._id, productId: savedProduct._id };
}

async function loginAdmin() {
  const res = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASS })
  });
  const data = await res.json();
  if (!data.token) throw new Error('Login failed: ' + JSON.stringify(data));
  return data.token;
}

async function run() {
  console.log('Setting up test data...');
  const { productId } = await setupData();
  console.log('Logging in...');
  const token = await loginAdmin();
  const headers = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };

  // 1. Add to Cart
  console.log('\n1. Add to Cart (Qty: 2)');
  let res = await fetch(`${API_URL}/cart/add`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ productId: PRODUCT_CODE, quantity: 2 })
  });
  console.log('Add Cart Status:', res.status);

  // 2. Create Order
  console.log('\n2. Create Order (Trigger Reservation)');
  res = await fetch(`${API_URL}/orders/create`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
          shippingAddress: { 
              fullName: 'Test User', 
              phone: '0987654321', 
              address: '123 Test Street, Ward 1, Dist 1', 
              city: 'HCM City' 
          },
          paymentMethod: 'MOMO'
      })
  });
  const orderData = await res.json();
  console.log('Order Status:', res.status);
  const orderId = orderData.orderId;

  // 3. Verify Stock (Expect Available: 8, Reserved: 2)
  console.log('\n3. Verify Stock (Expect Available: 8, Reserved: 2)');
  res = await fetch(`${API_URL}/products/${PRODUCT_CODE}`, { headers });
  let product = await res.json();
  console.log(`Stock: ${product.Stock_Quantity} (Exp: 8)`);
  console.log(`Reserved: ${product.Stock_Reserved} (Exp: 2)`);

  // 4. Update to Processing
  console.log('\n4. Update Order to Processing (Confirm Sale - Remove Reserved)');
  res = await fetch(`${API_URL}/orders/update-status`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ orderId, status: 'Processing' })
  });
  console.log('Update Status:', res.status);

  // 5. Verify Stock (Expect Available: 8, Reserved: 0)
  console.log('\n5. Verify Stock (Expect Available: 8, Reserved: 0)');
  res = await fetch(`${API_URL}/products/${PRODUCT_CODE}`, { headers });
  product = await res.json();
  console.log(`Stock: ${product.Stock_Quantity} (Exp: 8)`);
  console.log(`Reserved: ${product.Stock_Reserved} (Exp: 0)`);

  // 6. Verify History
  console.log('\n6. Verify Inventory Logs');
  res = await fetch(`${API_URL}/inventory/logs?productId=${PRODUCT_CODE}`, { headers });
  if (res.headers.get('content-type')?.includes('application/json')) {
      let logs = await res.json();
      console.log(`Log Count: ${logs.length}`);
      logs.forEach(l => console.log(`- ${l.action} (${l.changeAmount}): ${l.reason}`));
  }

  // 7. Update to Cancelled (Restock)
  // Need to create a new order or use adjust API to test restocking correctly if using previous logic
  // Let's test Inventory Adjust API
  console.log('\n7. Test Inventory Adjust API');
  res = await fetch(`${API_URL}/inventory/adjust`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({ productId: PRODUCT_CODE, newStock: 100, reason: 'Restock Huge' })
  });
  console.log('Adjust Status:', res.status);
  
  // 8. Verify Adjust
  res = await fetch(`${API_URL}/products/${PRODUCT_CODE}`, { headers });
  product = await res.json();
  console.log(`Stock after adjust: ${product.Stock_Quantity} (Exp: 100)`);
  
  // 9. Test Alerts
  console.log('\n9. Test Alerts');
  res = await fetch(`${API_URL}/inventory/alerts?threshold=1000`, { headers });
  const alerts = await res.json();
  console.log(`Low Stock Count: ${alerts.lowStock.count}`);

}

run().catch(e => { console.error(e); process.exitCode = 1; });
