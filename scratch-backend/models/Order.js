const mongoose = require('mongoose');

// Matches OrderResponse.OrderItemResponse SerializedName annotations
const orderItemSchema = new mongoose.Schema(
  {
    product: { type: String, required: true }, // productId (@SerializedName("product"))
    name: { type: String, required: true },
    quantity: { type: Number, required: true, min: 1 },
    price: { type: Number, required: true },
    shade: { type: String, default: '' },
  },
  { _id: false }
);

// Matches ShippingAddress.java SerializedName annotations
const shippingAddressSchema = new mongoose.Schema(
  {
    fullName: { type: String, required: true },
    phone: { type: String, required: true },
    address: { type: String, required: true }, // street/address line
    city: { type: String, required: true },
  },
  { _id: false }
);

const orderSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    status: {
      type: String,
      enum: ['Pending', 'Processing', 'Shipped', 'Delivered', 'Cancelled'],
      default: 'Pending',
    },
    totalPrice: { type: Number, required: true },
    paymentMethod: {
      type: String,
      enum: ['VNPAY', 'MOMO', 'CARD', 'COD'],
      required: true,
    },
    isPaid: { type: Boolean, default: false },
    shippingAddress: { type: shippingAddressSchema, required: true },
    items: { type: [orderItemSchema], required: true },
    // Backend-generated URL: /api/v1/orders/:id/invoice
    invoiceUrl: { type: String, default: '' },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Order', orderSchema);
