const Wishlist = require('../models/wishlist.model');
const Cart = require('../models/cart.model');
const Product = require('../models/product.model');

const WISHLIST_LIMIT = 50; // Limit items để tránh DDOS database
// 1. GET WISHLIST
exports.getWishlist = async (req, res) => {
    try {
        const userId = req.user.id;
        
        // Populate chi tiết sản phẩm
        let wishlist = await Wishlist.findOne({ user: userId })
            .populate('items.product', 'name price images countInStock slug Stock_Quantity Price'); // Lấy Stock & Price hiện tại

        if (!wishlist) {
            wishlist = await Wishlist.create({ user: userId, items: [] });
        }

        // Data Transformation
        // Server tự tính toán trạng thái để Frontend chỉ việc hiển thị
        const enrichedItems = wishlist.items.map(item => {
            const currentPrice = item.product.Price || item.product.price; // Support cả 2 naming convention
            const isPriceDropped = currentPrice < item.priceAtAdd;
            const isOutOfStock = (item.product.Stock_Quantity || item.product.countInStock) < 1;

            return {
                _id: item._id, // Wishlist Item ID
                productId: item.product._id,
                name: item.product.name,
                image: item.product.images ? item.product.images[0] : '',
                shade: item.shade,
                addedPrice: item.priceAtAdd,
                currentPrice: currentPrice,
                isPriceDropped, // Frontend dùng cờ này hiện badge "Sale"
                isOutOfStock,   // Frontend dùng cờ này disable nút "Add to Cart"
                addedAt: item.addedAt
            };
        });

        res.status(200).json({
            items: enrichedItems,
            count: enrichedItems.length
        });

    } catch (error) {
        console.error("Get Wishlist Error:", error);
        res.status(500).json({ message: "Lỗi server khi lấy wishlist" });
    }
};

// 2. ADD TO WISHLIST (Upsert Logic)
exports.addToWishlist = async (req, res) => {
    try {
        const userId = req.user.id;
        const { productId, shade } = req.body;

        // 1. Validate Product
        const product = await Product.findById(productId);
        if (!product) return res.status(404).json({ message: "Sản phẩm không tồn tại" });

        // 2. Find Wishlist
        let wishlist = await Wishlist.findOne({ user: userId });
        if (!wishlist) {
            wishlist = new Wishlist({ user: userId, items: [] });
        }

        // 3. Check Duplicate (Product + Shade)
        const exists = wishlist.items.find(
            item => item.product.toString() === productId && item.shade === (shade || '')
        );

        if (exists) {
            return res.status(400).json({ message: "Sản phẩm này đã có trong Yêu thích rồi!" });
        }

        // 4. Check Limit
        if (wishlist.items.length >= WISHLIST_LIMIT) {
            return res.status(400).json({ 
                message: `Danh sách yêu thích đã đầy (Tối đa ${WISHLIST_LIMIT} món). Hãy xóa bớt nhé!` 
            });
        }

        // 5. Add Item with Current Price
        wishlist.items.push({ 
            product: productId, 
            shade: shade || '',
            priceAtAdd: product.Price || product.price // Lưu giá thời điểm add
        });
        
        await wishlist.save();
        res.status(200).json({ message: "Đã thêm vào Yêu thích", count: wishlist.items.length });

    } catch (error) {
        console.error("Add Wishlist Error:", error);
        res.status(500).json({ message: "Lỗi server" });
    }
};

// 3. REMOVE FROM WISHLIST
exports.removeFromWishlist = async (req, res) => {
    try {
        const userId = req.user.id;
        const { productId } = req.params;
        // Hỗ trợ xóa chính xác theo shade (nếu user add 2 màu khác nhau của cùng 1 son)
        const shade = req.query.shade || ''; 

        const wishlist = await Wishlist.findOne({ user: userId });
        if (!wishlist) return res.status(404).json({ message: "Wishlist not found" });

        const initialLength = wishlist.items.length;

        // Filter Logic
        wishlist.items = wishlist.items.filter(
            item => !(item.product.toString() === productId && item.shade === shade)
        );

        if (wishlist.items.length === initialLength) {
            return res.status(404).json({ message: "Item không tìm thấy trong wishlist" });
        }

        await wishlist.save();
        res.status(200).json({ message: "Đã xóa thành công", items: wishlist.items });

    } catch (error) {
        console.error("Remove Wishlist Error:", error);
        res.status(500).json({ message: "Lỗi server" });
    }
};

// 4. MOVE TO CART (The "Pro" Feature)
// Logic: Add to Cart -> If Success -> Remove from Wishlist
exports.moveToCart = async (req, res) => {
    try {
        const userId = req.user.id;
        const { productId, shade } = req.body;

        // A. Validate Product Stock First (Defensive)
        const product = await Product.findById(productId);
        if (!product) return res.status(404).json({ message: "Sản phẩm không còn tồn tại" });
        
        const currentStock = product.Stock_Quantity || product.countInStock || 0;
        if (currentStock < 1) {
            return res.status(400).json({ message: "Sản phẩm này hiện đang hết hàng!" });
        }

        // B. Get User Cart
        let cart = await Cart.findOne({ user: userId });
        if (!cart) {
            cart = new Cart({ user: userId, items: [], totalPrice: 0 });
        }

        // C. Smart Merge Logic
        const cartItemIndex = cart.items.findIndex(
            p => p.product.toString() === productId && p.shade === shade
        );

        if (cartItemIndex > -1) {
            // Case 1: Đã có trong giỏ -> Tăng số lượng + Check lại tồn kho
            if (cart.items[cartItemIndex].quantity + 1 > currentStock) {
                return res.status(400).json({ message: "Bạn đã có số lượng tối đa trong giỏ hàng" });
            }
            cart.items[cartItemIndex].quantity += 1;
        } else {
            // Case 2: Chưa có -> Thêm mới
            cart.items.push({
                product: productId,
                quantity: 1,
                name: product.name,
                price: product.Price || product.price,
                image: product.images ? product.images[0] : '',
                shade: shade
            });
        }

        // Recalculate Cart Total
        cart.totalPrice = cart.items.reduce((acc, item) => acc + (item.price * item.quantity), 0);
        await cart.save();

        // D. Remove from Wishlist (Only run if Cart save success)
        await Wishlist.findOneAndUpdate(
            { user: userId },
            { 
                $pull: { 
                    items: { product: productId, shade: shade } 
                } 
            }
        );

        res.status(200).json({ message: "Đã chuyển vào giỏ hàng!", cartSize: cart.items.length });

    } catch (error) {
        console.error("Move to Cart Error:", error);
        res.status(500).json({ message: "Lỗi server: Không thể chuyển sang giỏ hàng" });
    }
};

// 5. CLEAR WISHLIST (Tiện ích phụ)
exports.clearWishlist = async (req, res) => {
    try {
        await Wishlist.findOneAndUpdate({ user: req.user.id }, { $set: { items: [] } });
        res.status(200).json({ message: "Đã xóa toàn bộ danh sách yêu thích" });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};