const mongoose = require('mongoose');
const admin = require('firebase-admin');
require('dotenv').config();

async function runTests() {
  console.log("=== Bắt đầu Audit scratch-backend ===");

  try {
    const serviceAccount = require('./config/serviceAccountKey.json');
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    console.log("✅ Firebase Admin Initialized Successfully.");
    
    const db = admin.firestore();
    const testDocRef = db.collection('audit_test').doc('ping');
    
    await testDocRef.set({ timestamp: Date.now(), message: 'Audit Test' });
    console.log("✅ Firestore Write Access: OK.");
    
  } catch (err) {
    console.error("❌ Firebase/Firestore Test Failed:", err.message);
  }

  process.exit(0);
}

runTests();
