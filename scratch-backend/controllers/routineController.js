const Order = require('../models/Order');
const Product = require('../models/Product');

// POST /api/v1/routines/save
exports.saveRoutine = async (req, res) => {
  try {
    const { items, isMorning } = req.body;
    
    if (!items || !Array.isArray(items)) {
      return res.status(400).json({ success: false, message: 'Invalid routine data' });
    }
    
    if (items.length > 8) {
      return res.status(400).json({ success: false, message: 'Max 8 products allowed per routine.' });
    }

    // Basic gamification: If they saved a routine successfully, they get a 5% voucher
    // In a real scenario, this would be saved to a Vouchers collection linked to the User.
    const gamificationVoucher = "TIRTIR_ROUTINE_5";

    return res.status(200).json({
      success: true,
      message: 'Routine saved successfully. Enjoy your reward!',
      voucher: gamificationVoucher
    });
  } catch (err) {
    console.error("Error saving routine:", err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};

// GET /api/v1/routines/recommendation
// Analyzes past orders to recommend a missing skincare step.
exports.getRecommendation = async (req, res) => {
  try {
    const userId = req.user.id;
    
    // Fetch user's orders
    const orders = await Order.find({ userId }).populate('items.productId');
    
    // Extract purchased product categories
    const purchasedCategories = new Set();
    orders.forEach(order => {
      order.items.forEach(item => {
        if (item.productId && item.productId.category) {
          purchasedCategories.add(item.productId.category.toLowerCase());
        }
      });
    });

    // Basic Skincare Loop: Cleanser, Toner, Serum, Moisturizer
    let suggestion = "";
    if (purchasedCategories.has('cleanser') && purchasedCategories.has('serum') && !purchasedCategories.has('toner')) {
      suggestion = "Dựa trên các sản phẩm bạn đã sở hữu, hãy thêm Toner TirTir này để tối ưu hóa hiệu quả kiềm dầu.";
    } else if (purchasedCategories.has('cleanser') && !purchasedCategories.has('moisturizer')) {
      suggestion = "Dựa trên lịch sử mua hàng, bạn nên bổ sung Moisturizer (Kem dưỡng) để khóa ẩm hoàn hảo.";
    } else if (!purchasedCategories.has('cleanser')) {
      suggestion = "Mọi chu trình đều bắt đầu từ bước làm sạch. Hãy thêm Cleanser vào chu trình của bạn.";
    } else {
      suggestion = "Bổ sung Serum đặc trị để nâng cao hiệu quả chu trình Skincare của bạn.";
    }

    return res.status(200).json({
      success: true,
      suggestion: suggestion
    });

  } catch (err) {
    console.error("Error generating recommendation:", err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
};
