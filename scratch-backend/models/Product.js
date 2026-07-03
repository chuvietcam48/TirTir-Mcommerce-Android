const mongoose = require('mongoose');

// Field names match Product.java SerializedName annotations exactly
// so MongoDB documents map directly to the Android model without transformation.
const productSchema = new mongoose.Schema(
  {
    Product_ID: { type: String },
    Parent_ID: { type: String },
    Category: { type: String, index: true },
    Category_Slug: { type: String, index: true },
    Name: { type: String, required: true },
    Product_Slug: { type: String },
    Price: { type: Number, default: 0 },
    Sale_Price: { type: Number, default: 0 },
    Volume_Size: { type: String },
    Is_Skincare: { type: String }, // "TRUE" | "FALSE"
    Skin_Type_Target: { type: String },
    Main_Concern: { type: String },
    Key_Ingredients: { type: String },
    Description_Short: { type: String },
    How_To_Use: { type: String },
    Full_Description: { type: String },
    Status: { type: String, default: 'active' },
    Stock_Quantity: { type: Number, default: 100 },
    Stock_Reserved: { type: Number, default: 0 },
    Thumbnail_Images: { type: String },
    Description_Images: [{ type: String }],
    Gallery_Images: [{ type: String }],
    slug: { type: String },
    shade_color_hex: { type: String },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Product', productSchema);
