const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const API_URL = 'http://localhost:5001/api/v1';
const ADMIN_EMAIL = 'admin@tirtir.com';
const ADMIN_PASS = 'admin123';
const USER_EMAIL = 'user_test_flow@tirtir.com';

async function setupData() {
    await mongoose.connect(process.env.MONGO_URI);
    const User = require('../models/user.model');
    const Order = require('../models/order.model');

    // Only cleanup test user and test orders, KEEP ADMIN
    await User.deleteOne({ email: USER_EMAIL });
    await Order.deleteMany({ 'shippingAddress.email': USER_EMAIL });

    const user = new User({ name: 'Test User', email: USER_EMAIL, password: 'password123', isEmailVerified: true });
    await user.save();
    
    // Admin is now persistent, do not delete/recreate
    
    await mongoose.connection.close();
}

async function loginAdmin() {
  const res = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASS })
  });
  const data = await res.json();
  if (!data.token) throw new Error('Login failed');
  return data.token;
}

async function run() {
  await setupAdmin();
  const token = await loginAdmin();

  const createPayload = {
    Product_ID: 'TEST-AUTO-001',
    Name: 'Tirtir Auto Test Cushion',
    Price: 350000,
    Category: 'Cushion',
    Description_Short: 'Automated test product',
    Stock_Quantity: 50
  };
  let r = await fetch(`${API_URL}/admin/products`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify(createPayload)
  });
  console.log('Create:', r.status, await r.json());

  r = await fetch(`${API_URL}/admin/products/TEST-AUTO-001`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify({ Price: 400000, Stock_Quantity: 45 })
  });
  console.log('Update:', r.status, await r.json());

  r = await fetch(`${API_URL}/admin/products/TEST-AUTO-001/stock`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify({ stock: 100 })
  });
  console.log('Stock:', r.status, await r.json());

  const bulkPayload = [
    { Product_ID: 'TEST-AUTO-001', Name: 'Tirtir Auto Test Cushion', Price: 420000, Category: 'Cushion', Stock_Quantity: 60 },
    { Product_ID: 'TEST-AUTO-002', Name: 'Tirtir Auto Test Serum', Price: 250000, Category: 'Serum', Stock_Quantity: 30 }
  ];
  r = await fetch(`${API_URL}/admin/products/bulk-import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify(bulkPayload)
  });
  console.log('Bulk:', r.status, await r.json());

  r = await fetch(`${API_URL}/admin/products/TEST-AUTO-001`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  console.log('Delete1:', r.status, await r.json());

  r = await fetch(`${API_URL}/admin/products/TEST-AUTO-002`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  console.log('Delete2:', r.status, await r.json());
}

run().catch(e => { console.error(e); process.exitCode = 1; });
