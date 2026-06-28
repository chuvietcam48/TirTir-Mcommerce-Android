const admin = require('firebase-admin');
const Routine = require('../models/Routine');
const Order = require('../models/Order');
const Product = require('../models/Product');
const User = require('../models/User');
const { grantVoucher } = require('../services/voucherService');

// POST /api/v1/routines/save
exports.saveRoutine = async (req, res) => {
  try {
    const { items, steps, isPublic, name, description, isMorning } = req.body;
    const userId = req.user ? req.user.id : null;

    if (!userId) {
      return res.status(401).json({ success: false, message: 'Unauthorized' });
    }

    const routineSteps = steps || items || [];
    if (!Array.isArray(routineSteps) || routineSteps.length === 0) {
      return res.status(400).json({ success: false, message: 'Steps or items array is required' });
    }

    if (routineSteps.length > 12) {
      return res.status(400).json({ success: false, message: 'Max 12 products allowed per routine.' });
    }

    const user = await User.findById(userId);
    const userName = user ? `${user.firstName} ${user.lastName}`.trim() : 'Anonymous';

    // 1. Save to MongoDB
    const routine = await Routine.create({
      userId,
      userName,
      name: name || 'My Custom Routine',
      description: description || '',
      steps: routineSteps.map((s, idx) => typeof s === 'object' ? s : { stepNumber: idx + 1, stepName: String(s) }),
      items: routineSteps,
      isPublic: !!isPublic,
      isMorning: isMorning !== undefined ? !!isMorning : true
    });

    const routineData = routine.toObject();
    let grantedVoucher = null;

    // 2. If isPublic == true, store into Firestore public_routines & MongoDB
    if (isPublic) {
      try {
        const db = admin.firestore();
        await db.collection('public_routines').doc(String(routine._id)).set({
          id: String(routine._id),
          userId: String(userId),
          userName,
          name: routine.name,
          description: routine.description,
          steps: routine.steps,
          likes: 0,
          isPublic: true,
          createdAt: admin.firestore.FieldValue.serverTimestamp()
        });
      } catch (fsErr) {
        console.error('[ROUTINE] Firestore public_routines sync error:', fsErr.message);
      }
    }

    // 3. If steps >= 4 automatically generate 5% voucher using real database records
    if (routineSteps.length >= 4) {
      try {
        grantedVoucher = await grantVoucher(userId, 5, '5% Routine Sharing Voucher', 30);
      } catch (vErr) {
        console.error('[ROUTINE] Voucher generation error:', vErr.message);
      }
    }

    return res.status(200).json({
      success: true,
      message: 'Routine saved successfully',
      data: routineData,
      voucher: grantedVoucher ? grantedVoucher.voucherCode : null,
      voucherDetails: grantedVoucher
    });
  } catch (err) {
    console.error('Error saving routine:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// GET /api/v1/routines/community
exports.getCommunityRoutines = async (req, res) => {
  try {
    const routines = await Routine.find({ isPublic: true }).sort({ likes: -1, createdAt: -1 }).limit(30).lean();
    res.status(200).json({ success: true, data: routines });
  } catch (err) {
    console.error('Error fetching community routines:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// POST /api/v1/routines/:id/like
exports.likeRoutine = async (req, res) => {
  try {
    const routineId = req.params.id;
    const routine = await Routine.findByIdAndUpdate(routineId, { $inc: { likes: 1 } }, { new: true });
    
    try {
      const db = admin.firestore();
      await db.collection('public_routines').doc(String(routineId)).update({
        likes: admin.firestore.FieldValue.increment(1)
      });
    } catch (e) {
      // ignore
    }

    res.status(200).json({ success: true, message: 'Liked successfully', likes: routine ? routine.likes : 0 });
  } catch (err) {
    console.error('Error liking routine:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// POST /api/v1/routines/:id/apply
exports.applyRoutine = async (req, res) => {
  try {
    const routineId = req.params.id;
    const userId = req.user ? req.user.id : null;
    if (!userId) return res.status(401).json({ success: false, message: 'Unauthorized' });

    const sourceRoutine = await Routine.findById(routineId);
    if (!sourceRoutine) return res.status(404).json({ success: false, message: 'Routine not found' });

    const newRoutine = await Routine.create({
      userId,
      name: `Applied: ${sourceRoutine.name}`,
      description: sourceRoutine.description,
      steps: sourceRoutine.steps,
      items: sourceRoutine.items,
      isPublic: false
    });

    res.status(200).json({ success: true, message: 'Routine applied to your active profile', data: newRoutine });
  } catch (err) {
    console.error('Error applying routine:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// GET /api/v1/routines/suggestion or /recommendation
// Uses user skinType, purchase history, missing skincare step instead of fixed JSON.
exports.getRecommendation = async (req, res) => {
  try {
    const userId = req.user ? req.user.id : null;
    let skinType = 'Chưa xác định';

    if (userId) {
      try {
        const db = admin.firestore();
        const doc = await db.collection('users').doc(String(userId)).get();
        if (doc.exists && doc.data().skinType) skinType = doc.data().skinType;
      } catch (e) {}
    }

    // Analyze past purchase history
    const orders = userId ? await Order.find({ userId }).lean() : [];
    const purchasedProductIds = [];
    orders.forEach(o => {
      o.items.forEach(i => {
        if (i.product) purchasedProductIds.push(String(i.product));
      });
    });

    const purchasedProducts = await Product.find({ _id: { $in: purchasedProductIds } }).lean();
    const purchasedCategories = new Set(purchasedProducts.map(p => (p.Category || '').toLowerCase()));

    let missingStep = 'Cleanser';
    let suggestionText = '';

    if (!purchasedCategories.has('cleanser') && !purchasedCategories.has('làm sạch')) {
      missingStep = 'Cleanser';
      suggestionText = `Mọi chu trình chăm sóc da ${skinType} đều cần bắt đầu bằng bước làm sạch sâu với Cleanser.`;
    } else if (!purchasedCategories.has('toner') && !purchasedCategories.has('nước hoa hồng')) {
      missingStep = 'Toner';
      suggestionText = `Da của bạn thuộc loại ${skinType}, việc cân bằng pH và cấp ẩm tức thì bằng Toner là rất cần thiết.`;
    } else if (!purchasedCategories.has('serum') && !purchasedCategories.has('tinh chất')) {
      missingStep = 'Serum';
      suggestionText = `Hãy bổ sung Serum chứa dưỡng chất chuyên sâu phù hợp cho da ${skinType} để cải thiện bề mặt da.`;
    } else {
      missingStep = 'Moisturizer';
      suggestionText = `Khóa ẩm hoàn hảo cho da ${skinType} giúp duy trì hàng rào bảo vệ da căng bóng cả ngày.`;
    }

    // Retrieve real database recommendations matching missing step
    const recommendedProducts = await Product.find({
      $or: [
        { Category: { $regex: new RegExp(missingStep, 'i') } },
        { Name: { $regex: new RegExp(missingStep, 'i') } },
        { Skin_Type_Target: { $regex: new RegExp(skinType, 'i') } }
      ],
      Stock_Quantity: { $gt: 0 }
    }).limit(4).lean();

    return res.status(200).json({
      success: true,
      data: {
        skinType,
        missingStep,
        suggestion: suggestionText,
        recommendedProducts
      }
    });
  } catch (err) {
    console.error("Error generating routine recommendation:", err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};
