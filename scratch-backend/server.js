require('dotenv').config();
const express = require('express');
const cors = require('cors');
const connectDB = require('./config/db');
const admin = require('firebase-admin');

try {
  const serviceAccount = require('./config/serviceAccountKey.json');
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    storageBucket: process.env.FIREBASE_STORAGE_BUCKET
  });
  console.log('[FIREBASE] Firebase Admin SDK initialized successfully via serviceAccountKey.json');
} catch (err) {
  console.warn('[FIREBASE] serviceAccountKey.json not found or failed to load. Trying default application credentials...', err.message);
  try {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      storageBucket: process.env.FIREBASE_STORAGE_BUCKET
    });
    console.log('[FIREBASE] Firebase Admin SDK initialized via Application Default Credentials');
  } catch (defaultErr) {
    console.error('[FIREBASE] Could not initialize Firebase Admin SDK. Firestore operations will fail.', defaultErr.message);
  }
}

const app = express();

connectDB().then(() => {
  try {
    require('./cron/cartRecoveryCron').start();
  } catch (e) {
    console.error('Failed to start cart recovery cron:', e.message);
  }
}).catch((err) => {
  console.error('MongoDB connection failed:', err.message);
  process.exit(1);
});

app.use(cors());
app.use(express.json());

app.use('/api/v1/auth', require('./routes/authRoutes'));
app.use('/api/v1/users', require('./routes/userRoutes'));
app.use('/api/v1/products', require('./routes/productRoutes'));
app.use('/api/v1/chat', require('./routes/chatRoutes'));
app.use('/api/v1/chatbot', require('./routes/chatbotRoutes'));
app.use('/api/chatbot', require('./routes/chatbotRoutes'));
app.use('/api/v1/ai', require('./routes/aiRoutes'));
app.use('/api/v1/cart', require('./routes/cartRoutes'));
app.use('/api/v1/wishlist', require('./routes/wishlistRoutes'));
app.use('/api/v1/vouchers', require('./routes/voucherRoutes'));
app.use('/api/v1/orders', require('./routes/orderRoutes'));
app.use('/api/v1/upload', require('./routes/uploadRoutes'));
app.use('/api/v1/admin', require('./routes/adminRoutes'));
app.use('/api/v1/tracking', require('./routes/trackingRoutes'));
app.use('/api/v1/routines', require('./routes/routineRoutes'));
app.use('/api/routines', require('./routes/routineRoutes'));

app.use('/api/v1/loyalty', require('./routes/loyaltyRoutes'));
app.use('/api/loyalty', require('./routes/loyaltyRoutes'));
app.use('/api/v1/payments', require('./routes/paymentRoutes'));
app.use('/soap', require('./shipping/ShippingSoapRouter'));
app.use('/api/shipping', require('./shipping/ShippingSoapRouter'));

app.get('/health', (_req, res) => res.json({ status: 'ok' }));

app.use((_req, res) => res.status(404).json({ success: false, message: 'Endpoint không tồn tại.' }));

app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ success: false, message: 'Lỗi máy chủ nội bộ.' });
});

const PORT = process.env.PORT || 5000;
app.listen(PORT, () => console.log(`TirTir API running on port ${PORT} [${process.env.NODE_ENV}]`));
