const mongoose = require('mongoose');

const wishlistSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  products: [{
    type: String, // Can store either MongoDB ObjectId or Product_ID (string)
    required: true
  }]
}, { timestamps: true });

module.exports = mongoose.model('Wishlist', wishlistSchema);
