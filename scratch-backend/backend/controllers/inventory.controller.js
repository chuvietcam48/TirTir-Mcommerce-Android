const Product = require('../models/product.model');
const StockHistory = require('../models/stock.history.model');
const Order = require('../models/order.model');
const mongoose = require('mongoose');
const { emitToAdmins } = require('../services/socket.service');

// 1. GET ALERTS (Sắp hết hàng & Bán chậm)
exports.getInventoryAlerts = async (req, res) => {
    try {
        const threshold = parseInt(req.query.threshold) || 10;
        
        // Low Stock
        const lowStock = await Product.find({ 
            Stock_Quantity: { $lt: threshold } 
        }).select('Product_ID Name Stock_Quantity Stock_Reserved Thumbnail_Images Category');

        // Dead Stock (No sales in 30 days - Simplified logic based on updatedAt not changing or creation)
        // Ideally we check Order history, but for now let's use UpdatedAt > 30 days and Stock > 0
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
        
        const deadStock = await Product.find({
            updatedAt: { $lt: thirtyDaysAgo },
            Stock_Quantity: { $gt: 0 }
        }).select('Product_ID Name Stock_Quantity updatedAt');

        res.json({
            lowStock: {
                count: lowStock.length,
                items: lowStock
            },
            deadStock: {
                count: deadStock.length,
                items: deadStock
            }
        });
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

// 1.5. GET INVENTORY STATS
exports.getInventoryStats = async (req, res) => {
    try {
        const totalProducts = await Product.countDocuments();
        const lowStockItems = await Product.countDocuments({ Stock_Quantity: { $lt: 10 } });
        const outOfStockItems = await Product.countDocuments({ Stock_Quantity: 0 });
        
        // Calculate Total Value of Inventory (Price * Quantity)
        const valueResult = await Product.aggregate([
            {
                $group: {
                    _id: null,
                    totalValue: { $sum: { $multiply: ["$Price", "$Stock_Quantity"] } }
                }
            }
        ]);
        
        const totalValue = valueResult.length > 0 ? valueResult[0].totalValue : 0;

        res.json({
            totalProducts,
            lowStockItems,
            outOfStockItems,
            totalValue
        });
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

// 2. ADJUST STOCK (Manual Correction)
exports.adjustStock = async (req, res) => {
    try {
        const { productId, newStock, reason } = req.body;
        
        if (newStock === undefined || newStock < 0) {
            return res.status(400).json({ message: "Invalid stock value" });
        }

        const product = await Product.findOne({ 
            $or: [{ Product_ID: productId }, { _id: mongoose.Types.ObjectId.isValid(productId) ? productId : null }]
        });

        if (!product) {
            return res.status(404).json({ message: "Product not found" });
        }

        const oldStock = product.Stock_Quantity;
        const diff = newStock - oldStock;
        
        if (diff === 0) {
            return res.json({ message: "No change in stock", product });
        }

        product.Stock_Quantity = newStock;

        // Use updateOne to bypass Mongoose validation on other dirty fields in the DB (like Is_Skincare='TRUE')
        await Product.updateOne(
            { _id: product._id }, 
            { $set: { Stock_Quantity: newStock } }
        );

        // Log History
        await StockHistory.create({
            product: product._id,
            action: 'Adjust',
            change_type: diff > 0 ? 'Increase' : 'Decrease',
            source_id: 'MANUAL_ADJUST',
            balance_before: oldStock,
            balance_after: newStock,
            changeAmount: Math.abs(diff),
            reason: reason || 'Manual Admin Adjustment',
            performedBy: req.user.id
        });

        res.json({ message: "Stock adjusted successfully", product });
        
        // ── Real-time: alert admins if stock is now low ───────────────────────
        const available = product.Stock_Quantity - (product.Stock_Reserved || 0);
        if (available < 10) {
            emitToAdmins('low_stock', {
                type: 'low_stock',
                title: 'Low Stock Alert',
                message: `${product.Name} — only ${available} left`,
                productId: product._id
            });
        }

    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

// 3. GET LOGS (Audit Trail)
exports.getStockLogs = async (req, res) => {
    try {
        const { productId, limit = 50 } = req.query;
        let query = {};

        if (productId) {
            const product = await Product.findOne({ 
                $or: [{ Product_ID: productId }, { _id: mongoose.Types.ObjectId.isValid(productId) ? productId : null }]
            });
            if (product) {
                query.product = product._id;
            }
        }

        const logs = await StockHistory.find(query)
            .populate('product', 'Name Product_ID')
            .populate('performedBy', 'name email')
            .sort({ createdAt: -1 })
            .limit(parseInt(limit));

        res.json(logs);
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

// 4. VALIDATE CART (Realtime Check)
exports.validateCart = async (req, res) => {
    try {
        const { items } = req.body; // Expect array of { productId, quantity }
        const errors = [];

        for (const item of items) {
            const product = await Product.findOne({ 
                $or: [{ Product_ID: item.productId }, { _id: mongoose.Types.ObjectId.isValid(item.productId) ? item.productId : null }]
            });

            if (!product) {
                errors.push({ productId: item.productId, message: "Product not found" });
                continue;
            }

            if (product.Stock_Quantity < item.quantity) {
                errors.push({ 
                    productId: item.productId, 
                    name: product.Name,
                    available: product.Stock_Quantity,
                    message: `Not enough stock. Available: ${product.Stock_Quantity}` 
                });
            }
        }

        if (errors.length > 0) {
            return res.status(400).json({ valid: false, errors });
        }

        res.json({ valid: true, message: "All items available" });

    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};

// 5. CLEANUP RESERVATIONS (Cron Job Endpoint)
// Release stock for Orders pending > 30 mins
exports.cleanupPendingOrders = async (req, res) => {
    try {
        const minutes = 30;
        const timeLimit = new Date(Date.now() - minutes * 60 * 1000);

        const expiredOrders = await Order.find({
            status: 'Pending',
            createdAt: { $lt: timeLimit }
        }).populate('items.product');

        let releasedCount = 0;

        for (const order of expiredOrders) {
            // 1. Update Order Status
            order.status = 'Cancelled';
            await order.save();

            // 2. Release Stock
            for (const item of order.items) {
                if (!item.product) continue;

                const product = await Product.findByIdAndUpdate(item.product._id, {
                    $inc: { 
                        Stock_Quantity: item.quantity,
                        Stock_Reserved: -item.quantity 
                    }
                }, { new: true });

                // 3. Log History
                if (product) {
                    await StockHistory.create({
                        product: product._id,
                        action: 'Release',
                        change_type: 'Increase',
                        source_id: order._id.toString(),
                        balance_before: product.Stock_Quantity - item.quantity,
                        balance_after: product.Stock_Quantity,
                        changeAmount: item.quantity,
                        reason: 'System Auto-Cancel (Expired Reservation)',
                        performedBy: req.user ? req.user.id : null // System action
                    });
                }
            }
            releasedCount++;
        }

        res.json({ 
            message: `Cleanup completed. Cancelled ${releasedCount} expired orders.`,
            count: releasedCount 
        });

    } catch (err) {
        res.status(500).json({ message: err.message });
    }
};
