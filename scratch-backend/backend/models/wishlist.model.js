const mongoose = require('mongoose');

const wishlistSchema = new mongoose.Schema({
    user: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        unique: true // Mỗi user chỉ có 1 wishlist document
    },
    items: [
        {
            product: {
                type: mongoose.Schema.Types.ObjectId,
                ref: 'Product',
                required: true
            },
            shade: {
                type: String,
                default: '' // Quan trọng cho mỹ phẩm (Cushion 21N, 23N...)
            },
            priceAtAdd: { // Lưu giá lúc user bấm tim
                type: Number,
                required: true
            },
            addedAt: {
                type: Date,
                default: Date.now
            }
        }
    ]
}, { timestamps: true });

// Index để tìm kiếm nhanh, tránh duplicate product+shade trong code (dù logic controller đã handle)
// wishlistSchema.index({ user: 1 }); // Removed duplicate index as user is unique: true

module.exports = mongoose.model('Wishlist', wishlistSchema);