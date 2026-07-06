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

const shippingAddressSchema = new mongoose.Schema(
  {
    fullName: { type: String, required: true },
    phone: { type: String, required: true },
    address: { type: String, required: true }, // street/address line
    city: { type: String, required: false },
    districtId: { type: String, required: false },
    wardCode: { type: String, required: false },
  },
  { _id: false }
);

const shippingDetailsSchema = new mongoose.Schema(
  {
    trackingNumber: { type: String, default: '' },
    carrier: { type: String, default: '' }, // e.g., GHN, VNPost
    estimatedDeliveryDate: { type: Date }
  },
  { _id: false }
);

const orderHistorySchema = new mongoose.Schema(
  {
    status: { type: String, required: true },
    timestamp: { type: Date, default: Date.now },
    note: { type: String, default: '' },
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
    shippingFee: { type: Number, default: 0 },
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
    idempotencyKey: { type: String, index: true },
    
    // Advanced Admin Order Management Fields
    adminNotes: { type: String, default: '' },
    cancellationReason: { type: String, default: '' },
    shippingDetails: { type: shippingDetailsSchema, default: () => ({}) },
    history: { type: [orderHistorySchema], default: [] }
  },
  { timestamps: true }
);

module.exports = mongoose.models.Order || mongoose.model('Order', orderSchema);
