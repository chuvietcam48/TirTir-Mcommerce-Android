require("dotenv").config({ path: require('path').join(__dirname, '.env') });
require("./instrument"); // Initialize Sentry

const http = require('http');
const express = require("express");
const cors = require("cors");
const mongoose = require("mongoose");
const path = require('path');
const helmet = require('helmet');
const mongoSanitize = require('express-mongo-sanitize');
const morgan = require('morgan');
const responseTime = require('response-time');
const Sentry = require("@sentry/node");
// Note: @sentry/profiling-node is loaded in instrument.js with graceful fallback

const errorHandler = require('./middlewares/error');
const logger = require('./utils/logger');
require('./cron/abandonedCart.cron'); // Initialize Cron Jobs
const socketService = require('./services/socket.service');

const { apiLimiter } = require('./middlewares/rateLimit');

// ─── Swagger API Docs ────────────────────────────────────────────────────────
const swaggerJsdoc = require('swagger-jsdoc');
const swaggerUi = require('swagger-ui-express');

const swaggerOptions = {
  definition: {
    openapi: '3.0.0',
    info: {
      title: 'TirTir Cosmetics API',
      version: '1.0.0',
      description: 'REST API documentation for TirTir Cosmetics E-Commerce Platform',
    },
    servers: [{ url: `http://localhost:${process.env.PORT || 5001}/api/v1`, description: 'Development server' }],
    components: {
      securitySchemes: {
        bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' },
      },
    },
    security: [{ bearerAuth: [] }],
  },
  apis: ['./routes/*.js'], // Auto-scan all route files for JSDoc annotations
};

const swaggerSpec = swaggerJsdoc(swaggerOptions);
// ─────────────────────────────────────────────────────────────────────────────

const app = express();
const server = http.createServer(app);
socketService.init(server);

// 1. Performance Monitoring (Response Time)
app.use(responseTime((req, res, time) => {
  const method = req.method;
  const url = req.originalUrl;
  const status = res.statusCode;

  // Log slow requests (> 500ms)
  if (time > 500) {
    logger.warn(`[SLOW REQUEST] ${method} ${url} ${status} - ${time.toFixed(2)}ms`);
  }
}));

// 2. Request Logging (Morgan -> Winston)
app.use(morgan('combined', { stream: logger.stream }));

// 3. CORS Configuration - MUST BE FIRST
// Production: strict whitelist from environment variables only
// Development: also allow any localhost/127.0.0.1 origin
const isProduction = process.env.NODE_ENV === 'production';

// Build the production whitelist from env vars (never hard-code domains)
const productionAllowedOrigins = new Set([
  process.env.FRONTEND_URL,        // e.g. https://tirtir.com
  process.env.ADMIN_URL,           // e.g. https://admin.tirtir.com  (optional)
].filter(Boolean)); // strip undefined entries

// Development convenience: port-agnostic localhost pattern
const localhostOriginPattern = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i;

app.use(cors({
  origin: (origin, callback) => {
    // Allow server-to-server or same-origin requests (no Origin header)
    if (!origin) {
      callback(null, true);
      return;
    }

    if (isProduction) {
      // Production: only explicitly listed origins are allowed
      if (productionAllowedOrigins.has(origin)) {
        callback(null, true);
      } else {
        logger.warn(`[CORS] Blocked origin in production: ${origin}`);
        callback(new Error(`Not allowed by CORS: ${origin}`));
      }
      return;
    }

    // Development: allow any localhost / 127.0.0.1 port for convenience
    if (localhostOriginPattern.test(origin)) {
      callback(null, true);
      return;
    }

    logger.warn(`[CORS] Blocked origin in development: ${origin}`);
    callback(new Error(`Not allowed by CORS: ${origin}`));
  },
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'sentry-trace', 'baggage', 'x-skip-token-refresh']
}));

// 2. Rate Limiting (Global API Protection) - placed after CORS
app.use('/api/', apiLimiter);

// Mongoose Debug Logging
if (process.env.NODE_ENV !== 'production') {
  mongoose.set('debug', (collectionName, method, query, doc) => {
    // Log DB queries to file/console via Winston
    logger.info(`[MONGO] ${collectionName}.${method} - ${JSON.stringify(query)}`);
  });
}


// Static Files - Add CORS headers manually
app.use('/assets', (req, res, next) => {
  res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  res.setHeader('Access-Control-Allow-Origin', '*');
  next();
}, express.static(path.join(__dirname, '../frontend/tirtir-frontend/public/assets'), {
  setHeaders: (res, filePath) => {
    res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
    res.setHeader('Access-Control-Allow-Origin', '*');
  }
}));

// Serve uploaded files (avatars, etc.)
app.use('/uploads', (req, res, next) => {
  res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  res.setHeader('Access-Control-Allow-Origin', '*');
  next();
}, express.static(path.join(__dirname, 'uploads'), {
  setHeaders: (res, filePath) => {
    res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
    res.setHeader('Access-Control-Allow-Origin', '*');
  }
}));


// Security Headers - Helmet with environment-aware CSP
const isDev = process.env.NODE_ENV !== 'production';
app.use(helmet({
  crossOriginResourcePolicy: { policy: "cross-origin" }, // Allow cross-origin images
  contentSecurityPolicy: {
    // useDefaults: true bao gồm các directive an toàn mặc định của helmet
    useDefaults: true,
    // Chế độ dev: reportOnly=true (không block, chỉ log vi phạm vào console)
    // Chế độ prod: enforce thật
    reportOnly: isDev,
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: [
        "'self'",
        // Angular cần 'unsafe-inline' cho inline event handlers khi chưa có nonce
        // Ở production nên migrate sang nonce-based CSP nếu có thể
        isDev ? "'unsafe-inline'" : null,
        isDev ? "'unsafe-eval'" : null, // Angular dev mode dùng eval
        "https://browser.sentry-cdn.com",
        "https://js.sentry-cdn.com",
      ].filter(Boolean),
      styleSrc: [
        "'self'",
        "'unsafe-inline'", // Angular component styles cần inline
        "https://fonts.googleapis.com",
      ],
      fontSrc: ["'self'", "https://fonts.gstatic.com", "data:"],
      imgSrc: [
        "'self'",
        "data:",
        "blob:",
        "https:", // Cho phép ảnh sản phẩm từ mọi HTTPS source
      ],
      connectSrc: [
        "'self'",
        // Sentry endpoints
        "https://*.sentry.io",
        "https://*.ingest.sentry.io",
        // Dev server Angular
        isDev ? "http://localhost:4200" : null,
        isDev ? "http://localhost:4201" : null,
        isDev ? "ws://localhost:4200" : null, // WebSocket HMR
        isDev ? "ws://localhost:4201" : null,
        isDev ? "ws://localhost:5001" : null, // Socket.io admin notifications
        isDev ? "http://localhost:5001" : null,
      ].filter(Boolean),
      mediaSrc: ["'self'", "blob:"],
      objectSrc: ["'none'"],
      frameAncestors: ["'none'"], // Chống Clickjacking
      upgradeInsecureRequests: isDev ? null : [], // Chỉ enforce HTTPS ở production
    },
  },
}));

app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Workaround: Express 5 làm req.query thành immutable getter,
// express-mongo-sanitize cần ghi vào req.query để sanitize.
// Middleware này redefine req.query thành writable trước khi sanitize chạy.
app.use((req, _res, next) => {
  const originalQuery = req.query; // Express 5 getter trả về parsed object
  try {
    Object.defineProperty(req, 'query', {
      value: { ...originalQuery }, // Snapshot thành plain object writable
      writable: true,
      configurable: true,
      enumerable: true,
    });
  } catch (_e) {
    // Nếu đã writable rồi (Express 4) thì bỏ qua
  }
  next();
});
app.use(mongoSanitize({
  replaceWith: '_', // Thay ký tự $ và . bằng _ thay vì xóa (tránh mất data structure)
  onSanitize: ({ req, key }) => {
    logger.warn(`[SECURITY] MongoSanitize: blocked injection attempt on key '${key}' - IP: ${req.ip}`);
  },
}));

// HTTPS Enforcement (Production only)
if (process.env.NODE_ENV === 'production') {
  app.use((req, res, next) => {
    if (req.header('x-forwarded-proto') !== 'https') {
      res.redirect(`https://${req.header('host')}${req.url}`);
    } else {
      next();
    }
  });
}

const shadeRoutes = require("./routes/shade.routes");
const productRoutes = require("./routes/product.routes");
const menuRoutes = require("./routes/menu.routes");
const { ensureSlugs } = require("./controllers/product.controller");
const paymentRoutes = require("./routes/payment.routes");
const wishlistRoutes = require("./routes/wishlist.routes");

app.get("/", (req, res) => res.send("API Running"));
app.get("/api/v1/health", (req, res) => res.json({ ok: true, msg: "alive" }));

// ─── Swagger Docs (Development Only) ────────────────────────────────────────
if (process.env.NODE_ENV !== 'production') {
  app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec, {
    customSiteTitle: 'TirTir API Docs',
    swaggerOptions: { persistAuthorization: true },
  }));
  app.get('/api-docs.json', (req, res) => res.json(swaggerSpec));
  logger.info('📚 Swagger UI available at http://localhost:5001/api-docs');
}
// ─────────────────────────────────────────────────────────────────────────────
app.get("/debug-sentry", function mainHandler(req, res) {
  Sentry.startSpan({
    op: "test",
    name: "My First Test Span",
  }, () => {
    try {
      // Send a log before throwing the error
      // Note: Sentry.logger might not be exposed in all versions, using captureMessage as fallback if needed
      // or standard console if enableLogs is true.
      // But strictly following user snippet:
      if (Sentry.logger) {
        Sentry.logger.info('User triggered test error', {
          action: 'test_error_span',
        });
      } else {
        console.log('User triggered test error', { action: 'test_error_span' });
      }

      // Send a test metric before throwing the error
      if (Sentry.metrics) {
        Sentry.metrics.count('test_counter', 1);
      }

      throw new Error("Sentry Test Error with Span & Metrics!");
    } catch (e) {
      Sentry.captureException(e);
      res.status(500).send("Sentry Test Error Triggered! Check Sentry Dashboard.");
    }
  });
});

// API Routes
app.use("/api/v1/shades", shadeRoutes);
app.use("/api/v1/products", productRoutes);
app.use("/api/v1/admin/smart", require("./routes/admin.smart.routes")); // Add Smart Admin Routes
app.use("/api/v1/admin/products", require("./routes/admin.products.routes"));
app.use("/api/v1/admin/users", require("./routes/admin.users.routes")); // Add Admin User Routes
app.use("/api/v1/admin", require("./routes/admin.dashboard.routes"));
app.use("/api/v1/menus", menuRoutes);
app.use("/api/v1/auth", require("./routes/auth.routes"));
app.use("/api/v1/cart", require("./routes/cart.routes"));
app.use("/api/v1/chat", require("./routes/chat.routes"));
app.use("/api/v1/orders", require("./routes/order.routes"));
app.use("/api/v1/users", require("./routes/user.routes"));
app.use("/api/v1/reviews", require("./routes/review.routes")); // Add Review Routes
app.use("/api/v1/inventory", require("./routes/inventory.routes"));
app.use("/api/v1/admin/stats", require("./routes/admin.stats.routes"));
app.use("/api/v1/analytics", require("./routes/analytics.routes"));
app.use("/api/v1/payments", paymentRoutes);
app.use("/api/v1/coupons", require("./routes/coupon.routes"));
app.use("/api/v1/upload", require("./routes/upload.routes")); // Add Upload Routes
app.use("/api/v1/ai", require("./routes/ai.routes"));
app.use("/api/v1/wishlist", wishlistRoutes);
app.use("/api/v1/settings", require("./routes/setting.routes")); // Add Settings Routes
app.use("/api/v1/marketing", require("./routes/marketing.routes")); // Add Marketing Routes
app.use("/api/v1/notifications", require("./routes/notification.routes")); // Add Notification Routes
app.use("/api/v1/shipping", require("./routes/shipping.routes")); // GHN Shipping Integration

// Sentry Error Handler (Must be before any other error middleware)
if (process.env.SENTRY_DSN) {
  Sentry.setupExpressErrorHandler(app);
}

// Global Error Handler
app.use(errorHandler);

const PORT = process.env.PORT || 5001;
const MONGO_URI = process.env.MONGO_URI;

async function start() {
  try {
    await mongoose.connect(MONGO_URI);
    console.log("MongoDB connected");
    await ensureSlugs(); // Auto-populate slugs

    // Start GHN status polling cron job (fallback for missed webhooks)
    const { startGHNPollingJob } = require('./jobs/checkGHNStatus');
    startGHNPollingJob();

    server.listen(PORT, () => console.log(`Server running: http://localhost:${PORT}`));
  } catch (err) {
    console.error("Startup error:", err.message);
    process.exit(1);
  }
}

start();
