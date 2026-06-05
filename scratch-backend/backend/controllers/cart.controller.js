const Cart = require('../models/cart.model');
const Product = require('../models/product.model');
const jwt = require('jsonwebtoken');
const mongoose = require('mongoose');

// Helper to recalculate total price
const calculateTotal = (cart) => {
    return cart.items.reduce((total, item) => total + (item.price * item.quantity), 0);
};

const normalizeId = (value) => {
    if (value === undefined || value === null) {
        return '';
    }

    if (typeof value === 'string' || typeof value === 'number') {
        return String(value).trim();
    }

    if (typeof value === 'object') {
        if (typeof value.toHexString === 'function') {
            return value.toHexString().trim();
        }

        if (value.$oid !== undefined && value.$oid !== null) {
            return String(value.$oid).trim();
        }

        if (value._id !== undefined && value._id !== null) {
            const nestedId = value._id;
            if (typeof nestedId === 'string' || typeof nestedId === 'number') {
                return String(nestedId).trim();
            }
            if (nestedId && typeof nestedId.toHexString === 'function') {
                return nestedId.toHexString().trim();
            }
            if (nestedId && typeof nestedId.toString === 'function') {
                const nestedStr = nestedId.toString().trim();
                if (nestedStr && nestedStr !== '[object Object]') {
                    return nestedStr;
                }
            }
        }

        if (value.id !== undefined && value.id !== null && value.id !== value) {
            const virtualId = value.id;
            if (typeof virtualId === 'string' || typeof virtualId === 'number') {
                return String(virtualId).trim();
            }
        }

        if (typeof value.toString === 'function') {
            const raw = value.toString().trim();
            if (raw && raw !== '[object Object]') {
                return raw;
            }
        }

        return '';
    }

    return String(value).trim();
};

const normalizeShade = (value) => {
    const normalized = normalizeId(value).trim();
    if (!normalized || normalized === 'null' || normalized === 'undefined') {
        return '';
    }
    return normalized;
};

const buildProductLookupQuery = (rawProductId) => {
    const normalizedProductId = normalizeId(rawProductId);
    if (!normalizedProductId) {
        return null;
    }
    if (mongoose.Types.ObjectId.isValid(normalizedProductId)) {
        return { $or: [{ _id: normalizedProductId }, { Product_ID: normalizedProductId }] };
    }
    return { Product_ID: normalizedProductId };
};

// 1. ADD TO CART
exports.addToCart = async (req, res) => {
    console.log("==========================================");
    console.log("DEBUG: POST /api/v1/cart/add called");
    console.log("1. Request Body received:", req.body);

    try {
        // DEFENSIVE CHECK 1: User
        if (!req.user || !req.user.id) {
            console.error("2. User Info: MISSING req.user or req.user.id");
            return res.status(401).json({ message: "Unauthorized: No user found in request" });
        }
        const userId = req.user.id;
        console.log("2. User Info:", userId);

        const { productId, quantity, shade } = req.body;
        const normalizedRequestProductId = normalizeId(productId);

        // DEFENSIVE CHECK 2: Inputs
        if (!productId) {
            console.error("3. Validation: Missing productId");
            return res.status(400).json({ message: "ProductId is required" });
        }
        if (!quantity || quantity <= 0) {
            console.error("3. Validation: Invalid quantity", quantity);
            return res.status(400).json({ message: "Positive Quantity is required" });
        }

        // 3. Find Product
        console.log("3. Product finding for ID (String):", normalizedRequestProductId);
        const productLookupQuery = buildProductLookupQuery(normalizedRequestProductId);
        const product = productLookupQuery ? await Product.findOne(productLookupQuery) : null;

        if (!product) {
            console.error("❌ Product NOT FOUND in DB for ID:", productId);
            return res.status(404).json({ message: "Product not found" });
        }
        console.log("✅ Product Found:", product.Name, "| ID:", product._id, "| Stock:", product.Stock_Quantity);

        // Normalize shade
        const finalShade = normalizeShade(shade);
        const normalizedProductObjectId = normalizeId(product?._id);
        console.log("   Final Shade used:", finalShade);

        // 4. Get Cart
        console.log("4. Finding User Cart...");
        let cart = await Cart.findOne({ user: userId });

        if (!cart) {
            console.log("   Cart not found, creating new one.");
            cart = new Cart({
                user: userId,
                items: []
            });
        } else {
            console.log("   Cart found. Items count:", cart.items.length);
        }

        // Logic check stock
        let currentQtyInCart = 0;
        const existingItem = cart.items.find((item) => {
            const itemProductId = normalizeId(item.product);
            const itemShade = normalizeShade(item.shade);
            return itemProductId === normalizedProductObjectId && itemShade === finalShade;
        });
        if (existingItem) {
            currentQtyInCart = existingItem.quantity;
        }

        console.log("   Stock Check -> Req:", quantity, "+ InCart:", currentQtyInCart, "vs Stock:", product.Stock_Quantity);
        if ((currentQtyInCart + quantity) > product.Stock_Quantity) {
            console.error("   ❌ Insufficient stock");
            return res.status(400).json({
                message: `Insufficient stock. Available: ${product.Stock_Quantity}, In Cart: ${currentQtyInCart}`
            });
        }

        // 5. Update Items
        const itemIndex = cart.items.findIndex((item) => {
            const itemProductId = normalizeId(item.product);
            const itemShade = normalizeShade(item.shade);
            return itemProductId === normalizedProductObjectId && itemShade === finalShade;
        });

        if (itemIndex > -1) {
            console.log("   Updating existing item at index:", itemIndex);
            cart.items[itemIndex].quantity += quantity;
            cart.items[itemIndex].price = product.Price; // Update price check
        } else {
            console.log("   Pushing NEW item to array");
            cart.items.push({
                product: product._id, // IMPORTANT: Use the ObjectId from the FOUND product doc
                quantity: quantity,
                shade: finalShade,
                price: product.Price
            });
        }

        // Recalculate
        console.log("6. Recalculating Total...");
        // Fallback for missing prices is good, but let's keep it simple for debug
        let total = 0;
        cart.items.forEach(item => {
            total += (item.price || 0) * item.quantity;
        });
        cart.totalPrice = total;
        cart.recoveryStatus = 'pending';
        cart.lastAbandonedAt = new Date(); // Reset timestamp when cart is updated
        console.log("   Total Price:", total);

        await cart.save();
        console.log("✅ Cart Saved Successfully!");

        const populatedCart = await Cart.findById(cart._id).populate({
            path: 'items.product',
            select: 'Name Price Original_Price Thumbnail_Images Stock_Quantity slug Product_Attributes Product_ID'
        });
        res.status(200).json(populatedCart);

    } catch (error) {
        console.error("🔥 FATAL ERROR in addToCart:", error);
        console.error(error.stack); // PRINT FULL STACK
        res.status(500).json({ message: "Server error", error: error.message });
    }
    console.log("==========================================");
};

// 2. UDPATE CART ITEM (PUT)
exports.updateCartItem = async (req, res) => {
    try {
        const userId = req.user.id;
        const { productId, shade, quantity, oldShade, newShade } = req.body;
        const normalizedRequestProductId = normalizeId(productId);

        // Note: quantity here is the NEW TARGET quantity, not delta
        if (quantity < 0) {
            return res.status(400).json({ message: "Quantity cannot be negative" });
        }

        const cart = await Cart.findOne({ user: userId });
        if (!cart) {
            return res.status(404).json({ message: "Cart not found" });
        }

        // Use oldShade if provided (Quick Edit), otherwise use shade (Standard Update)
        const targetShade = normalizeShade((oldShade !== undefined) ? oldShade : shade);

        const productLookupQuery = buildProductLookupQuery(normalizedRequestProductId);
        const product = productLookupQuery
            ? await Product.findOne(productLookupQuery).select('_id Product_ID Stock_Quantity Price')
            : null;
        const normalizedProductObjectId = product
            ? normalizeId(product._id)
            : normalizedRequestProductId;

        const itemIndex = cart.items.findIndex((item) => {
            const itemProductId = normalizeId(item.product);
            const itemShade = normalizeShade(item.shade);
            return itemProductId === normalizedProductObjectId && itemShade === targetShade;
        });

        if (itemIndex === -1) {
            return res.status(404).json({ message: "Item not found in cart" });
        }

        // If quantity is 0, remove item
        if (quantity === 0) {
            cart.items.splice(itemIndex, 1);
        } else {
            // Validate Stock for the NEW quantity
            if (!product) {
                // If product deleted, remove item
                cart.items.splice(itemIndex, 1);
            } else if (quantity > product.Stock_Quantity) {
                return res.status(400).json({
                    message: `Insufficient stock. Max available: ${product.Stock_Quantity}`
                });
            } else {
                cart.items[itemIndex].quantity = quantity;
                cart.items[itemIndex].price = product.Price;

                // Handle Quick Attribute Edit
                const normalizedNewShade = normalizeShade(newShade);
                if (newShade !== undefined && normalizedNewShade !== targetShade) {
                    // Check if the NEW shade already exists in the cart to merge them
                    const existingNewShadeIndex = cart.items.findIndex(
                        (item, index) => {
                            const itemProductId = normalizeId(item.product);
                            const itemShade = normalizeShade(item.shade);
                            return index !== itemIndex && itemProductId === normalizedProductObjectId && itemShade === normalizedNewShade;
                        }
                    );

                    if (existingNewShadeIndex > -1) {
                        // Merge quantities
                        cart.items[existingNewShadeIndex].quantity += quantity;
                        // Remove the old item
                        cart.items.splice(itemIndex, 1);
                    } else {
                        // Just update the shade string
                        cart.items[itemIndex].shade = normalizedNewShade;
                    }
                }
            }
        }

        cart.totalPrice = calculateTotal(cart);
        cart.recoveryStatus = 'pending';
        cart.lastAbandonedAt = new Date();
        await cart.save();

        const populatedCart = await Cart.findById(cart._id).populate({
            path: 'items.product',
            select: 'Name Price Original_Price Thumbnail_Images Stock_Quantity slug Product_Attributes Product_ID'
        });
        res.status(200).json(populatedCart);

    } catch (error) {
        console.error("Update Cart Error:", error);
        res.status(500).json({ message: "Server error" });
    }
};

// 3. REMOVE FROM CART (DELETE)
exports.removeFromCart = async (req, res) => {
    try {
        const userId = req.user.id;
        const { productId, shade } = req.params;
        // Note: Shade might be "undefined" string if passed from some frontends, handle strictly

        const cart = await Cart.findOne({ user: userId });
        if (!cart) {
            return res.status(404).json({ message: "Cart not found" });
        }

        // Use strict filter
        const originalLength = cart.items.length;
        cart.items = cart.items.filter(
            item => !(item.product.toString() === productId && item.shade === shade)
        );

        if (cart.items.length === originalLength) {
            // Item check logic
            // Try check if shade was passed as "null" string
            if (shade === 'null') {
                cart.items = cart.items.filter(
                    item => !(item.product.toString() === productId && !item.shade)
                );
            }
        }

        cart.totalPrice = calculateTotal(cart);
        cart.recoveryStatus = 'pending';
        cart.lastAbandonedAt = new Date();
        await cart.save();

        const populatedCart = await Cart.findById(cart._id).populate({
            path: 'items.product',
            select: 'Name Price Original_Price Thumbnail_Images Stock_Quantity slug Product_Attributes Product_ID'
        });
        res.status(200).json(populatedCart);

    } catch (error) {
        console.error("Remove from Cart Error:", error);
        res.status(500).json({ message: "Server error" });
    }
};

// 4. CLEAR CART
exports.clearCart = async (req, res) => {
    try {
        const userId = req.user.id;
        const cart = await Cart.findOne({ user: userId });

        if (cart) {
            cart.items = [];
            cart.totalPrice = 0;
            await cart.save();
        }

        res.status(200).json({ message: "Cart cleared", items: [], totalPrice: 0 });

    } catch (error) {
        console.error("Clear Cart Error:", error);
        res.status(500).json({ message: "Server error" });
    }
};

// 5. GET CART (with automatic cleanup of null products)
exports.getCart = async (req, res) => {
    try {
        const userId = req.user.id;
        let cart = await Cart.findOne({ user: userId }).populate({
            path: 'items.product',
            select: 'Name Price Original_Price Thumbnail_Images Stock_Quantity slug Product_Attributes Product_ID'
        });

        if (!cart) {
            // Return empty cart structure instead of 404 to allow frontend to handle gracefully
            return res.status(200).json({ items: [], totalPrice: 0 });
        }

        // Clean up null products (e.g., deleted from database)
        const originalLength = cart.items.length;
        cart.items = cart.items.filter(item => item.product != null);

        if (cart.items.length !== originalLength) {
            cart.totalPrice = calculateTotal(cart);
            await cart.save();
        }

        res.status(200).json(cart);

    } catch (error) {
        console.error("Get Cart Error:", error);
        res.status(500).json({ message: "Server error" });
    }
};

// 6. GET CART COUNT (Badge)
exports.getCartCount = async (req, res) => {
    try {
        const userId = req.user.id;
        const cart = await Cart.findOne({ user: userId }); // No populate needed

        if (!cart) {
            return res.status(200).json({ count: 0 });
        }

        const count = cart.items.reduce((sum, item) => sum + item.quantity, 0);
        res.status(200).json({ count });

    } catch (error) {
        console.error("Get Cart Count Error:", error);
        res.status(500).json({ message: "Server error" });
    }
};

// 7. UNSUBSCRIBE FROM CART RECOVERY EMAILS
exports.unsubscribeRecovery = async (req, res) => {
    const successHtml = `<html><body style="font-family:sans-serif;text-align:center;padding:60px"><h2>You've been unsubscribed</h2><p>You won't receive any more cart reminders.</p></body></html>`;
    const errorHtml = `<html><body style="font-family:sans-serif;text-align:center;padding:60px"><h2>Invalid or expired link</h2><p>This unsubscribe link is no longer valid. Please check your email for an updated link.</p></body></html>`;

    try {
        const { token } = req.query;
        if (!token) {
            return res.status(400).send(errorHtml);
        }

        let decoded;
        try {
            decoded = jwt.verify(token, process.env.JWT_SECRET);
        } catch (err) {
            return res.status(400).send(errorHtml);
        }

        const { userId, cartId } = decoded;

        const cart = await Cart.findOne({ _id: cartId, user: userId });
        if (!cart) {
            return res.status(400).send(errorHtml);
        }

        cart.recoveryStatus = 'unsubscribed';
        cart.unsubscribedAt = Date.now();
        await cart.save();

        return res.send(successHtml);

    } catch (error) {
        console.error("Unsubscribe Recovery Error:", error);
        return res.status(400).send(errorHtml);
    }
};

// 8. MERGE CART (G4 Cart Merge Logic)
exports.mergeCart = async (req, res) => {
    try {
        const { guestCartToken, mergeStrategy = 'union' } = req.body;
        const userId = req.user.id;

        const supportTransactions = mongoose.connection.readyState === 1 && !!mongoose.connection.db.admin;
        const session = supportTransactions ? await mongoose.startSession() : null;
        
        try {
            if (session) session.startTransaction();

            const guestCart = await Cart.findOne({ recovery_token: guestCartToken }).session(session);
            if (!guestCart || guestCart.status === 'purchased') throw new Error('INVALID_TOKEN');
            if (guestCart.token_expires_at && new Date() > guestCart.token_expires_at) throw new Error('TOKEN_EXPIRED');

            let userCart = await Cart.findOne({ user: userId, status: { $ne: 'merged' } }).session(session);
            if (!userCart) {
                userCart = new Cart({ user: userId, items: [], status: 'active' });
            }

            const mergedMap = new Map();
            userCart.items.forEach(item => {
                const key = item.product.toString() + '_' + (item.shade || '');
                mergedMap.set(key, { ...item.toObject() });
            });

            guestCart.items.forEach(gItem => {
                const key = gItem.product.toString() + '_' + (gItem.shade || '');
                if (mergedMap.has(key)) {
                    mergedMap.get(key).quantity += gItem.quantity;
                } else {
                    mergedMap.set(key, { ...gItem.toObject() });
                }
            });

            userCart.items = Array.from(mergedMap.values());
            userCart.totalPrice = calculateTotal(userCart);
            userCart.lastAbandonedAt = new Date(); 

            const itemsAdded = guestCart.items.length;

            guestCart.status = 'merged';
            guestCart.merged_into_cart_id = userCart._id;

            await guestCart.save({ session });
            await userCart.save({ session });

            let CartRecoveryEvent;
            try { CartRecoveryEvent = require('../models/cart_recovery_event.model'); } catch(e) {}
            if (CartRecoveryEvent) {
                await CartRecoveryEvent.create([{
                    cart_id: guestCart._id,
                    user_id: userId,
                    event_type: 'cart_merged',
                    metadata: { target_cart: userCart._id }
                }], { session });
            }

            try {
                const { cancelRecoveryJobsForCart } = require('../queues/cart_recovery.queue');
                if (cancelRecoveryJobsForCart) await cancelRecoveryJobsForCart(guestCart._id.toString());
            } catch (e) {}

            if (session) await session.commitTransaction();

            const populatedCart = await Cart.findById(userCart._id).populate({
                path: 'items.product',
                select: 'Name Price Original_Price Thumbnail_Images Stock_Quantity slug Product_Attributes Product_ID'
            });

            res.status(200).json({ mergedCart: populatedCart, itemsAdded });
        } catch (error) {
            if (session) await session.abortTransaction();
            throw error;
        } finally {
            if (session) session.endSession();
        }
    } catch (error) {
        if (['INVALID_TOKEN', 'TOKEN_EXPIRED'].includes(error.message)) {
            return res.status(400).json({ error: error.message });
        }
        res.status(500).json({ error: 'MERGE_FAILED', message: error.message });
    }
};

// 9. CART ABANDONMENT TRIGGER (Triggered via API optionally on beforeunload)
exports.abandonCart = async (req, res) => {
    try {
        const userId = req.user ? req.user.id : null;
        let cart;
        
        if (userId) {
            const CartClass = require('../models/cart.model');
            cart = await CartClass.findOne({ user: userId }).populate('user');
        }
        
        const { guestEmail, guestCartToken } = req.body;
        if (!cart && guestCartToken) {
            const CartClass = require('../models/cart.model');
            cart = await CartClass.findOne({ recovery_token: guestCartToken });
        }

        if (!cart) return res.status(404).json({ message: "Cart not found" });

        const { scheduleRecoveryEmails } = require('../services/cart_abandonment.service');
        await scheduleRecoveryEmails(cart, userId ? cart.user?.email : null, guestEmail);

        cart.status = 'abandoned';
        await cart.save();

        res.status(200).json({ message: "Scheduled cart recovery events." });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "Server error" });
    }
};
