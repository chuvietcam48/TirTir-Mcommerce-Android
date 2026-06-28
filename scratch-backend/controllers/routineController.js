const admin = require('firebase-admin');
const Routine = require('../models/Routine');
const Order = require('../models/Order');
const Product = require('../models/Product');
const User = require('../models/User');
const { grantVoucher } = require('../services/voucherService');

// POST /api/routines/save or /api/v1/routines/save
exports.saveRoutine = async (req, res) => {
  try {
    const { userId: bodyUserId, name, steps, items, isPublic, description, isMorning } = req.body;
    const userId = (req.user && (req.user.id || req.user._id)) ? String(req.user.id || req.user._id) : (bodyUserId ? String(bodyUserId) : null);

    // Validation
    if (!userId) {
      return res.status(400).json({ success: false, message: 'userId is required' });
    }
    if (!name || typeof name !== 'string' || name.trim() === '') {
      return res.status(400).json({ success: false, message: 'name is required' });
    }
    const routineSteps = steps || items;
    if (!Array.isArray(routineSteps) || routineSteps.length === 0) {
      return res.status(400).json({ success: false, message: 'steps must be a non-empty array' });
    }

    // Validate each step
    const formattedSteps = [];
    for (let i = 0; i < routineSteps.length; i++) {
      const step = routineSteps[i];
      if (typeof step === 'string') {
        formattedSteps.push({
          stepType: step,
          stepName: step,
          order: i + 1
        });
      } else if (typeof step === 'object' && step !== null) {
        if (!step.stepType && !step.stepName) {
          return res.status(400).json({ success: false, message: `Step at index ${i} must have stepType` });
        }
        const stepOrder = step.order !== undefined ? Number(step.order) : i + 1;
        if (isNaN(stepOrder)) {
          return res.status(400).json({ success: false, message: `Step order at index ${i} must be numeric` });
        }
        formattedSteps.push({
          stepType: step.stepType || step.stepName,
          productId: step.productId ? String(step.productId) : undefined,
          order: step.order !== undefined ? Number(step.order) : i + 1,
          stepName: step.stepName || step.stepType,
          productName: step.productName,
          notes: step.notes || ''
        });
      } else {
        return res.status(400).json({ success: false, message: `Invalid step format at index ${i}` });
      }
    }

    let userName = 'Anonymous';
    try {
      const userDoc = await User.findById(userId);
      if (userDoc) {
        userName = `${userDoc.firstName || ''} ${userDoc.lastName || ''}`.trim() || userDoc.email || 'Anonymous';
      } else {
        const db = admin.firestore();
        const fsUser = await db.collection('users').doc(userId).get();
        if (fsUser.exists) {
          const uData = fsUser.data();
          userName = uData.name || uData.displayName || `${uData.firstName || ''} ${uData.lastName || ''}`.trim() || 'Anonymous';
        }
      }
    } catch (e) {
      // Ignore user fetch errors
    }

    // 1. Save to MongoDB
    const routine = await Routine.create({
      userId,
      ownerId: userId,
      userName,
      name: name.trim(),
      description: description || '',
      steps: formattedSteps,
      isPublic: !!isPublic,
      likeCount: 0,
      likes: 0,
      applyCount: 0,
      isMorning: isMorning !== undefined ? !!isMorning : true
    });

    const routineId = String(routine._id);
    let voucherGranted = false;
    let voucherObj = null;

    // 2. If isPublic === true, copy public routine to Firestore public_routines/{routineId}
    if (isPublic) {
      try {
        const db = admin.firestore();
        const productIds = formattedSteps.map(s => s.productId).filter(Boolean);
        await db.collection('public_routines').doc(routineId).set({
          routineId,
          id: routineId,
          ownerId: userId,
          userName,
          name: routine.name,
          description: routine.description,
          steps: formattedSteps,
          productIds,
          likeCount: 0,
          likes: 0,
          applyCount: 0,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          isPublic: true
        });
      } catch (fsErr) {
        console.error('[ROUTINE] Firestore public_routines sync error:', fsErr.message);
      }
    }

    // 3. If isPublic === true AND steps.length >= 4 -> grant voucher
    if (isPublic && formattedSteps.length >= 4) {
      try {
        voucherObj = await grantVoucher(userId, {
          discountPct: 5,
          reason: 'PUBLIC_ROUTINE_REWARD',
          source: 'routine',
          expiryDays: 30
        });
        voucherGranted = true;
      } catch (vErr) {
        console.error('[ROUTINE] Voucher generation error:', vErr.message);
      }
    }

    return res.status(201).json({
      success: true,
      message: 'Routine saved successfully',
      routineId,
      isPublic: !!isPublic,
      voucherGranted,
      voucher: voucherObj ? {
        code: voucherObj.code || voucherObj.voucherCode,
        discountPct: voucherObj.discountPct,
        expiryDate: voucherObj.expiryDate
      } : null,
      data: routine
    });
  } catch (err) {
    console.error('Error saving routine:', err);
    res.status(500).json({ success: false, message: 'Server error saving routine' });
  }
};

// GET /api/routines/community
exports.getCommunityRoutines = async (req, res) => {
  try {
    const limit = parseInt(req.query.limit, 10) || 20;
    const sortParam = req.query.sort || 'popular';
    const page = parseInt(req.query.page, 10) || 1;
    const skip = (page - 1) * limit;

    const sortOption = sortParam === 'latest' 
      ? { createdAt: -1 } 
      : { likeCount: -1, likes: -1, createdAt: -1 };

    const routines = await Routine.find({ isPublic: true })
      .sort(sortOption)
      .skip(skip)
      .limit(limit)
      .lean();

    const formattedRoutines = routines.map(r => ({
      routineId: String(r._id),
      _id: r._id,
      name: r.name,
      ownerId: r.ownerId || String(r.userId),
      userName: r.userName || 'Anonymous',
      steps: r.steps || [],
      likeCount: r.likeCount !== undefined ? r.likeCount : (r.likes || 0),
      applyCount: r.applyCount || 0,
      createdAt: r.createdAt
    }));

    return res.status(200).json({
      success: true,
      data: formattedRoutines
    });
  } catch (err) {
    console.error('Error fetching community routines:', err);
    res.status(500).json({ success: false, message: 'Server error fetching community routines' });
  }
};

// POST /api/routines/:id/like
exports.likeRoutine = async (req, res) => {
  try {
    const routineId = req.params.id;
    const { userId: bodyUserId } = req.body;
    const userId = (req.user && (req.user.id || req.user._id)) ? String(req.user.id || req.user._id) : (bodyUserId ? String(bodyUserId) : null);

    if (!userId) {
      return res.status(400).json({ success: false, message: 'userId is required' });
    }

    // Load routine
    let routine = await Routine.findById(routineId);
    if (!routine) {
      return res.status(404).json({ success: false, message: 'Routine not found' });
    }

    // Anti-duplicate check using Firestore marker & MongoDB likedBy
    const db = admin.firestore();
    let alreadyLiked = false;

    if (routine.likedBy && routine.likedBy.includes(String(userId))) {
      alreadyLiked = true;
    }

    if (!alreadyLiked) {
      try {
        const likeDoc = await db.collection('public_routines')
          .doc(String(routineId))
          .collection('likes')
          .doc(String(userId))
          .get();
        if (likeDoc.exists) {
          alreadyLiked = true;
        }
      } catch (fsErr) {
        // Ignore firestore read error fallback
      }
    }

    if (alreadyLiked) {
      return res.status(409).json({ success: false, message: 'User has already liked this routine' });
    }

    // Store anti-duplicate marker & update counters
    routine = await Routine.findByIdAndUpdate(
      routineId,
      {
        $inc: { likeCount: 1, likes: 1 },
        $addToSet: { likedBy: String(userId) }
      },
      { new: true }
    );

    try {
      await db.collection('public_routines').doc(String(routineId)).collection('likes').doc(String(userId)).set({
        likedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      await db.collection('public_routines').doc(String(routineId)).update({
        likeCount: admin.firestore.FieldValue.increment(1),
        likes: admin.firestore.FieldValue.increment(1)
      });
    } catch (fsErr) {
      // Ignore firestore write error
    }

    const updatedLikeCount = routine ? (routine.likeCount !== undefined ? routine.likeCount : routine.likes) : 0;

    return res.status(200).json({
      success: true,
      routineId: String(routineId),
      likeCount: updatedLikeCount
    });
  } catch (err) {
    console.error('Error liking routine:', err);
    res.status(500).json({ success: false, message: 'Server error liking routine' });
  }
};

// POST /api/routines/:id/apply
exports.applyRoutine = async (req, res) => {
  try {
    const routineId = req.params.id;
    const { userId: bodyUserId } = req.body;
    const userId = (req.user && (req.user.id || req.user._id)) ? String(req.user.id || req.user._id) : (bodyUserId ? String(bodyUserId) : null);

    if (!userId) {
      return res.status(400).json({ success: false, message: 'userId is required' });
    }

    const sourceRoutine = await Routine.findById(routineId);
    if (!sourceRoutine) {
      return res.status(404).json({ success: false, message: 'Routine not found' });
    }

    // Create new private routine for user
    const newRoutine = await Routine.create({
      userId,
      ownerId: userId,
      userName: sourceRoutine.userName,
      name: `Applied: ${sourceRoutine.name}`,
      description: sourceRoutine.description,
      steps: sourceRoutine.steps,
      isPublic: false,
      likeCount: 0,
      likes: 0,
      applyCount: 0
    });

    // Increment applyCount on source routine
    await Routine.findByIdAndUpdate(routineId, { $inc: { applyCount: 1 } });
    try {
      const db = admin.firestore();
      await db.collection('public_routines').doc(String(routineId)).update({
        applyCount: admin.firestore.FieldValue.increment(1)
      });
    } catch (fsErr) {
      // Ignore firestore update error
    }

    return res.status(200).json({
      success: true,
      sourceRoutineId: String(routineId),
      newRoutineId: String(newRoutine._id),
      data: newRoutine
    });
  } catch (err) {
    console.error('Error applying routine:', err);
    res.status(500).json({ success: false, message: 'Server error applying routine' });
  }
};

// GET /api/routines/suggest?userId=...&missingStep=...
exports.getRecommendation = async (req, res) => {
  try {
    const queryUserId = req.query.userId;
    const missingStepArg = req.query.missingStep;
    const userId = (req.user && (req.user.id || req.user._id)) ? String(req.user.id || req.user._id) : (queryUserId ? String(queryUserId) : null);

    if (!userId) {
      return res.status(400).json({ success: false, message: 'userId is required' });
    }
    if (!missingStepArg) {
      return res.status(400).json({ success: false, message: 'missingStep is required' });
    }

    const missingStep = String(missingStepArg).trim();

    // Load user skinType from Firestore users/{userId}
    let skinType = 'oily';
    try {
      const db = admin.firestore();
      const userDoc = await db.collection('users').doc(String(userId)).get();
      if (userDoc.exists && userDoc.data().skinType) {
        skinType = String(userDoc.data().skinType).toLowerCase();
      }
    } catch (e) {
      // Fallback
    }

    // Load order history from MongoDB to identify owned/purchased products
    const purchasedProductIds = new Set();
    try {
      const orders = await Order.find({
        $or: [
          { userId: userId },
          { userId: String(userId) }
        ]
      }).lean();

      orders.forEach(order => {
        if (Array.isArray(order.items)) {
          order.items.forEach(item => {
            if (item.product) {
              purchasedProductIds.add(String(item.product));
            }
          });
        }
      });
    } catch (e) {
      // Ignore order query error
    }

    // Fetch candidate products matching missingStep and skinType
    const candidateProducts = await Product.find({
      Status: { $ne: 'inactive' },
      Stock_Quantity: { $gt: 0 },
      $or: [
        { Category: { $regex: new RegExp(missingStep, 'i') } },
        { Name: { $regex: new RegExp(missingStep, 'i') } },
        { Category_Slug: { $regex: new RegExp(missingStep, 'i') } },
        { Skin_Type_Target: { $regex: new RegExp(skinType, 'i') } }
      ]
    }).limit(20).lean();

    // Separate unowned vs owned products to prefer unowned items
    const unownedProducts = [];
    const ownedProducts = [];

    candidateProducts.forEach(p => {
      const pId = String(p.Product_ID || p._id);
      if (purchasedProductIds.has(pId)) {
        ownedProducts.push(p);
      } else {
        unownedProducts.push(p);
      }
    });

    const finalProducts = [...unownedProducts, ...ownedProducts].slice(0, 5);

    const suggestions = finalProducts.map(p => {
      const cat = p.Category || missingStep;
      return {
        productId: String(p.Product_ID || p._id),
        name: p.Name,
        category: cat,
        reason: `Phù hợp với loại da ${skinType} và bổ sung bước ${missingStep} còn thiếu.`
      };
    });

    return res.status(200).json({
      success: true,
      skinType,
      missingStep,
      suggestions
    });
  } catch (err) {
    console.error('Error in routine suggestion:', err);
    res.status(500).json({ success: false, message: 'Server error generating routine suggestion' });
  }
};

