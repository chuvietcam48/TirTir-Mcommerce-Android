const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const API_URL = 'http://localhost:5001/api/v1';
const ADMIN_EMAIL = 'admin@tirtir.com';
const ADMIN_PASS = 'admin123';
const TEST_PRODUCT_ID = 'TEST-CRUD-001';

async function setupAdmin() {
  await mongoose.connect(process.env.MONGO_URI);
  // No longer needed to delete/recreate admin every time
  // Just ensure products are clean
  const Product = require('../models/product.model');
  
  // Cleanup
  await Product.deleteOne({ Product_ID: TEST_PRODUCT_ID });
  await Product.deleteMany({ Product_ID: { $regex: 'BULK-' } });
  
  await mongoose.connection.close();
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
  console.log('Setting up admin...');
  await setupAdmin();
  console.log('Logging in...');
  const token = await loginAdmin();
  const headers = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };

  console.log('\n--- Testing Product CRUD ---');

  // 1. Create Product
  console.log('1. POST /api/admin/products (Create)');
  let res = await fetch(`${API_URL}/admin/products`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
          Product_ID: TEST_PRODUCT_ID,
          Name: 'Test CRUD Product',
          Price: 50,
          Category: 'Test',
          Stock_Quantity: 10,
          Thumbnail_Images: 'test.jpg'
      })
  });
  console.log('Status:', res.status);
  let data = await res.json();
  console.log('Created:', data.id);
  const dbId = data._id || data.id; // handle mapped vs raw

  // 2. Update Product
  console.log('\n2. PUT /api/admin/products/:id (Update)');
  res = await fetch(`${API_URL}/admin/products/${TEST_PRODUCT_ID}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({
          Name: 'Test CRUD Product Updated',
          Price: 55
      })
  });
  console.log('Status:', res.status);
  data = await res.json();
  console.log('Updated Name:', data.Name);

  // 3. Update Stock
  console.log('\n3. PATCH /api/admin/products/:id/stock (Update Stock)');
  res = await fetch(`${API_URL}/admin/products/${TEST_PRODUCT_ID}/stock`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({
          stock: 100,
          reason: 'Restock Test'
      })
  });
  console.log('Status:', res.status);
  console.log('Response:', await res.json());

  // 4. Verify Stock History
  console.log('\n4. Verify Stock History');
  res = await fetch(`${API_URL}/products/${TEST_PRODUCT_ID}/stock-history`, { headers });
  console.log('History Status:', res.status);
  data = await res.json();
  if (Array.isArray(data)) {
      console.log('History Count:', data.length);
      if(data.length > 0) {
          console.log('Last Log:', data[0].action, data[0].changeAmount, data[0].reason);
      }
  } else {
      console.log('History Error/Response:', data);
  }

  // 5. Bulk Import
  console.log('\n5. POST /api/admin/products/bulk-import');
  res = await fetch(`${API_URL}/admin/products/bulk-import`, {
      method: 'POST',
      headers,
      body: JSON.stringify([
          { Product_ID: 'BULK-001', Name: 'Bulk 1', Price: 10, Category: 'Test', Stock_Quantity: 5 },
          { Product_ID: 'BULK-002', Name: 'Bulk 2', Price: 20, Category: 'Test', Stock_Quantity: 5 }
      ])
  });
  console.log('Status:', res.status);
  console.log('Response:', await res.json());

  // 6. Delete Product
  console.log('\n6. DELETE /api/admin/products/:id');
  res = await fetch(`${API_URL}/admin/products/${TEST_PRODUCT_ID}`, {
      method: 'DELETE',
      headers
  });
  console.log('Status:', res.status);
  console.log('Response:', await res.json());

}

run().catch(e => { console.error(e); process.exitCode = 1; });