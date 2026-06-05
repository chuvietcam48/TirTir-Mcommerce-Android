const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const API_URL = 'http://localhost:5001/api/v1';
const ADMIN_EMAIL = 'admin@tirtir.com';
const ADMIN_PASS = 'admin123';

async function setupAdmin() {
  // Admin is already persistent, no need to recreate
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
  console.log('Setting up admin user...');
  await setupAdmin();
  console.log('Logging in...');
  const token = await loginAdmin();

  const headers = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };

  console.log('\n--- Testing Dashboard APIs ---');

  // 1. Stats
  console.log('GET /admin/dashboard/stats');
  let res = await fetch(`${API_URL}/admin/dashboard/stats`, { headers });
  console.log('Status:', res.status);
  console.log('Body:', await res.json());

  // 2. Revenue
  console.log('\nGET /admin/dashboard/revenue');
  res = await fetch(`${API_URL}/admin/dashboard/revenue`, { headers });
  console.log('Status:', res.status);
  console.log('Body:', await res.json()); // Might be long, but it's fine for test

  // 3. Top Products
  console.log('\nGET /admin/dashboard/top-products');
  res = await fetch(`${API_URL}/admin/dashboard/top-products`, { headers });
  console.log('Status:', res.status);
  console.log('Body:', await res.json());

  // 4. Customers
  console.log('\nGET /admin/dashboard/customers');
  res = await fetch(`${API_URL}/admin/dashboard/customers`, { headers });
  console.log('Status:', res.status);
  console.log('Body:', await res.json());

  // 5. Orders
  console.log('\nGET /admin/orders');
  res = await fetch(`${API_URL}/admin/orders?limit=2`, { headers });
  console.log('Status:', res.status);
  console.log('Body:', await res.json());
}

run().catch(e => { console.error(e); process.exitCode = 1; });
