const mongoose = require('mongoose');

const ReviewSchema = new mongoose.Schema({
    user: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true
    },
    product: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Product',
        required: true
    },
    rating: {
        type: Number,
        min: 1,
        max: 5,
        required: [true, 'Please add a rating between 1 and 5']
    },
    title: {
        type: String,
        required: [true, 'Please add a title for the review'],
        trim: true,
        maxlength: 100
    },
    comment: {
        type: String,
        required: [true, 'Please add some text for the review'],
        maxlength: 1000
    },
    images: {
        type: [String],
        default: []
    },
    verifiedPurchase: {
        type: Boolean,
        default: false
    },
    helpful: [{
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User'
    }]
}, {
    timestamps: true
});

// Prevent user from submitting more than one review per product
ReviewSchema.index({ product: 1, user: 1 }, { unique: true });

// Static method to get avg rating and save
ReviewSchema.statics.getAverageRating = async function(productId) {
    const obj = await this.aggregate([
        {
            $match: { product: productId }
        },
        {
            $group: {
                _id: '$product',
                averageRating: { $avg: '$rating' },
                count: { $sum: 1 }
            }
        }
    ]);

    try {
        await this.model('Product').findByIdAndUpdate(productId, {
            Rating_Average: obj[0] ? Math.round(obj[0].averageRating * 10) / 10 : 0,
            Rating_Count: obj[0] ? obj[0].count : 0
        });
    } catch (err) {
        console.error(err);
    }
};

// Call getAverageRating after save
ReviewSchema.post('save', function() {
    this.constructor.getAverageRating(this.product);
});

// Call getAverageRating after remove
ReviewSchema.post('remove', function() {
    this.constructor.getAverageRating(this.product);
});

// Also handle findOneAndRemove/Delete in modern Mongoose
ReviewSchema.post('findOneAndDelete', async function(doc) {
    if (doc) {
        await doc.constructor.getAverageRating(doc.product);
    }
});

module.exports = mongoose.model('Review', ReviewSchema);
