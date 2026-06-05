const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const { assert, request } = require('./test_utils');
const API_URL = 'http://localhost:5000/api';
async function run() {
  let r = await request('POST', '/admin/products', { Product_ID: 'ADM-UNAUTH-1', Name: 'NoToken', Price: 1, Category: 'Cushion' });
  assert(r.status === 401, 'no_token');
  await mongoose.connect(process.env.MONGO_URI);
  const User = require('../models/user.model');
  await User.deleteOne({ email: 'user_test_suite@tirtir.com' });
  const salt = await bcrypt.genSalt(10);
  const hashedPassword = await bcrypt.hash('user123', salt);
  const u = new User({ name: 'Suite User', email: 'user_test_suite@tirtir.com', password: hashedPassword, role: 'user', isEmailVerified: true });
  await u.save();
  await mongoose.connection.close();
  const res = await fetch(`${API_URL}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: 'user_test_suite@tirtir.com', password: 'user123' }) });
  const data = await res.json();
  const token = data.token;
  r = await request('POST', '/admin/products', { Product_ID: 'ADM-UNAUTH-2', Name: 'UserToken', Price: 1, Category: 'Cushion' }, token);
  assert(r.status === 403, 'user_token_forbidden');
  console.log('UNAUTH_OK');
}
run().catch(e => { console.error(e); process.exitCode = 1; });
