const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const API_URL = 'http://localhost:5001/api';
const ADMIN_EMAIL = 'admin_test_suite@tirtir.com';
const ADMIN_PASS = 'admin123';
async function ensureAdminAndToken() {
  await mongoose.connect(process.env.MONGO_URI);
  const User = require('../models/user.model');
  await User.deleteOne({ email: ADMIN_EMAIL });
  const salt = await bcrypt.genSalt(10);
  const hashedPassword = await bcrypt.hash(ADMIN_PASS, salt);
  const admin = new User({ name: 'Suite Admin', email: ADMIN_EMAIL, password: hashedPassword, role: 'admin', isEmailVerified: true });
  await admin.save();
  await mongoose.connection.close();
  const res = await fetch(`${API_URL}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASS }) });
  const data = await res.json();
  if (!data.token) throw new Error('login_failed');
  return data.token;
}
function assert(condition, msg) {
  if (!condition) {
    console.error('ASSERT_FAIL', msg);
    process.exitCode = 1;
    throw new Error(msg);
  }
}
async function request(method, pathUrl, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${API_URL}${pathUrl}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let data;
  try { data = await res.json(); } catch (_) { data = await res.text(); }
  return { status: res.status, data };
}
module.exports = { ensureAdminAndToken, assert, request };
