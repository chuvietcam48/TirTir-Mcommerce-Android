const express = require("express");
const router = express.Router();
const {
    getAllProducts,
    advancedSearch,
    getSearchSuggestions,
    getProductFilters,
    getLowStockProducts,
    getProductStockHistory,
    getProductById
} = require("../controllers/product.controller");
const reviewRouter = require("./review.routes"); // Import review routes
const { protect, authorize } = require('../middlewares/auth');
const { cacheMiddleware } = require('../middlewares/cache');

// Re-route into other resource routers
router.use('/:id/reviews', reviewRouter);

// Apply caching to Get All Products (5 minutes = 300 seconds)
router.get("/", cacheMiddleware(300), getAllProducts);

// Advanced Search & Filter Routes
router.get('/search', advancedSearch);
router.get('/search/suggestions', getSearchSuggestions);
router.get('/filters', getProductFilters);

// Admin Stock Routes (Must be before /:id to avoid conflict)
router.get("/low-stock", protect, authorize('admin', 'inventory'), getLowStockProducts);
router.get("/:id/stock-history", protect, authorize('admin', 'inventory'), getProductStockHistory);

router.get("/:id", getProductById);

module.exports = router;