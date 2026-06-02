const mongoose = require("mongoose");

const ShadeSchema = new mongoose.Schema(
  {
    Product_ID: { type: String, required: true, index: true },

    Shade_Code: { type: String, required: true },
    Shade_Category_Name: { type: String, required: true },
    Parent_ID: { type: String, required: true },
    Shade_ID: { type: String, required: true, unique: true, index: true },

    Shade_Name: { type: String, required: true },
    Hex_Code: { type: String, required: true },
    Shade_Image: { type: String },
    Shade_Type: { type: String },
    Stock_Quantity: { type: Number, default: 0, min: 0 },

    Hydration: { type: mongoose.Schema.Types.Mixed },
    Coverage: { type: mongoose.Schema.Types.Mixed },
    Oxidation_Level: { type: mongoose.Schema.Types.Mixed },

    // RGB
    R: { type: Number },
    G: { type: Number },
    B: { type: Number },

    // LAB
    L: { type: Number },
    a: { type: Number },
    b: { type: Number },

    Skin_Tone: String,
    Skin_Type: String,
    Undertone: String,
    Finish_Type: String,
    Coverage_Profile: String,
    Oxidation_Risk_Level: String,

    No: { type: Number, index: true }
  },
  { collection: "shades" }
);

module.exports = mongoose.model("Shade", ShadeSchema);