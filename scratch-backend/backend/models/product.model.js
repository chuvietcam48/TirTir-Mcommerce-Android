const mongoose = require('mongoose');

const ProductSchema = new mongoose.Schema({
    Product_ID: { type: String, required: true, unique: true }, // PRD-MK-RED
    Parent_ID: String,
    Category: String,
    Name: String,
    Price: Number,
    Volume_Size: String,
    Is_Skincare: Boolean,
    Description_Short: String,
    Full_Description: String,
    How_To_Use: String,

    // Mảng đường dẫn ảnh
    Thumbnail_Images: String,
    Gallery_Images: [String],
    Description_Images: [String],

    // Embedded Shades REMOVED (Stored in separate 'shades' collection)

    // Additional Fields
    Is_Best_Seller: { type: Boolean, default: false },
    Rating_Average: { type: Number, default: 0 },
    Rating_Count: { type: Number, default: 0 },
    Sold_Quantity: { type: Number, default: 0 },
    Category_Slug: { type: String, index: true },
    slug: { type: String, unique: true, sparse: true },

    // Attributes for filtering
    Skin_Type_Target: String,
    Main_Concern: String,

    Status: String,
    Stock_Quantity: { type: Number, default: 0, min: 0 },
    Stock_Reserved: { type: Number, default: 0, min: 0 }, // Items held in pending orders
    
    // Analytics
    views: { type: Number, default: 0 }
}, { collection: 'products', timestamps: true });

// Add Text Index for Advanced Search
ProductSchema.index({ Name: 'text', Description_Short: 'text', Full_Description: 'text' });

module.exports = mongoose.model('Product', ProductSchema);