'use strict';
/**
 * Seed script for TirTir chat data.
 * Run once after deploy: node scripts/seedChatData.js
 *
 * Requires FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_SERVICE_ACCOUNT_PATH in .env
 */

require('dotenv').config({ path: require('path').join(__dirname, '..', '.env') });

const admin = require('firebase-admin');
const { getFirestore } = require('firebase-admin/firestore');
const fs    = require('fs');
const path  = require('path');

function initFirebase() {
    const existingApps = (admin.apps ?? null) || admin.getApps();
    if (existingApps.length > 0) return getFirestore();

    const projectId          = process.env.FIREBASE_PROJECT_ID;
    const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
    const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;

    let keyObject = null;
    if (serviceAccountJson) {
        keyObject = JSON.parse(serviceAccountJson);
    } else if (serviceAccountPath) {
        const abs = path.isAbsolute(serviceAccountPath)
            ? serviceAccountPath
            : path.join(__dirname, '..', serviceAccountPath);
        keyObject = JSON.parse(fs.readFileSync(abs, 'utf8'));
    }

    if (!keyObject) throw new Error('No Firebase credentials. Set FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_SERVICE_ACCOUNT_PATH in .env');

    admin.initializeApp({
        credential: admin.credential?.cert
            ? admin.credential.cert(keyObject)
            : admin.cert(keyObject),
        projectId: projectId || keyObject.project_id
    });
    return getFirestore();
}

// ── chatConfig ────────────────────────────────────────────────────────────────

const chatConfig = {
    welcomeMessage:  "I'm your {botName}. I can help with:\n• Skincare routines\n• Product recommendations\n• Ingredient safety\n• Order support\n\nHotline: {hotline}\n\nWhat would you like to do?",
    hotline:         '1900-1234',
    retentionHours:  24,
    botName:         'TIRTIR Beauty Advisor',
    botAvatarUrl:    null,
    quickChips: [
        'Serum dùng trước hay sau kem dưỡng?',
        'Routine da dầu nên dùng gì?',
        'Kiểm tra tình trạng đơn hàng',
        'Hướng dẫn dùng AI phân tích da'
    ]
};

// ── Q&A Dataset ───────────────────────────────────────────────────────────────

const questions = [

    // ── ROUTINE ───────────────────────────────────────────────────────────────

    {
        intentCode:  'routine_serum_before_moisturizer',
        category:    'routine',
        question:    'Should serum be used before or after moisturizer?',
        aliases: [
            'serum trước hay sau kem dưỡng',
            'serum before or after moisturizer',
            'serum dùng trước hay sau kem dưỡng',
            'dùng serum trước hay kem dưỡng',
            'thứ tự serum và kem dưỡng',
            'serum before or after cream',
            'which one goes first serum or moisturizer',
            'serum hay kem dưỡng trước'
        ],
        keywords: ['serum', 'kem dưỡng', 'moisturizer', 'trước', 'sau', 'thứ tự', 'before', 'after', 'cream', 'order', 'first'],
        answerText: 'Serum nên được dùng TRƯỚC kem dưỡng ẩm.\n\nThứ tự đúng:\n1. Tẩy trang & rửa mặt\n2. Toner / nước cân bằng\n3. Serum (thẩm thấu sâu nhất)\n4. Kem mắt (nếu có)\n5. Kem dưỡng ẩm (khóa ẩm)\n6. Kem chống nắng (buổi sáng)\n\nLý do: Serum có phân tử nhỏ, cần tiếp xúc trực tiếp với da để thẩm thấu hiệu quả. Nếu bôi kem dưỡng trước, lớp này sẽ tạo rào cản khiến serum không thấm được vào da.',
        suggestProductIds: [],
        priority: 1,
        isActive: true
    },
    {
        intentCode:  'routine_morning_order',
        category:    'routine',
        question:    'What is the correct morning skincare order?',
        aliases: [
            'thứ tự skincare buổi sáng',
            'morning skincare routine',
            'routine buổi sáng',
            'các bước dưỡng da buổi sáng',
            'bước chăm sóc da sáng'
        ],
        keywords: ['buổi sáng', 'morning', 'thứ tự', 'routine', 'bước', 'chăm sóc da', 'skincare', 'sáng'],
        answerText: 'Thứ tự skincare buổi sáng chuẩn:\n\n1. Rửa mặt với sữa rửa mặt dịu nhẹ\n2. Toner / nước cân bằng da\n3. Serum (vitamin C, hyaluronic acid, v.v.)\n4. Kem mắt\n5. Kem dưỡng ẩm\n6. Kem chống nắng SPF 30+ (bắt buộc!)\n\nTip: Không cần dùng quá nhiều sản phẩm — 3–4 bước cơ bản cũng đủ tốt. Kem chống nắng là bước quan trọng nhất buổi sáng, không nên bỏ qua!',
        suggestProductIds: [],
        priority: 2,
        isActive: true
    },
    {
        intentCode:  'routine_for_oily_skin',
        category:    'routine',
        question:    'What routine is suitable for oily skin?',
        aliases: [
            'da dầu nên dùng gì',
            'routine da dầu',
            'skincare cho da dầu',
            'sản phẩm cho da dầu',
            'oily skin routine',
            'what to use for oily skin'
        ],
        keywords: ['da dầu', 'oily', 'routine', 'bóng nhờn', 'dầu thừa', 'lỗ chân lông', 'pore', 'nhờn'],
        answerText: 'Với da dầu, bạn nên ưu tiên:\n\n✅ Nên dùng:\n• Sữa rửa mặt tạo bọt kiểm soát dầu\n• Toner chứa niacinamide hoặc BHA (salicylic acid)\n• Serum niacinamide (thu nhỏ lỗ chân lông)\n• Kem dưỡng ẩm dạng gel (oil-free)\n• Kem chống nắng dạng fluid/gel không dầu\n\n❌ Tránh:\n• Sản phẩm chứa dầu khoáng hoặc lanolin\n• Kem quá giàu dưỡng chất (heavy cream)\n• Bỏ qua dưỡng ẩm (da dầu vẫn cần dưỡng ẩm!)\n\nTIRTIR có nhiều sản phẩm phù hợp da dầu. Bạn muốn tôi gợi ý cụ thể không?',
        suggestProductIds: [],
        priority: 3,
        isActive: true
    },
    {
        intentCode:  'routine_for_dry_skin',
        category:    'routine',
        question:    'What routine is good for dry skin?',
        aliases: [
            'da khô nên dùng gì',
            'routine da khô',
            'sản phẩm cho da khô',
            'dry skin routine',
            'chăm sóc da khô'
        ],
        keywords: ['da khô', 'dry', 'khô', 'bong tróc', 'thiếu ẩm', 'moisturize', 'hydrate', 'ẩm'],
        answerText: 'Với da khô, tập trung vào dưỡng ẩm sâu:\n\n✅ Nên dùng:\n• Sữa rửa mặt dạng kem/foam nhẹ (không chứa SLS)\n• Toner cấp nước với hyaluronic acid\n• Serum hyaluronic acid hoặc ceramide\n• Kem dưỡng ẩm giàu dưỡng chất (rich cream)\n• Dầu dưỡng ban đêm (facial oil)\n• Kem chống nắng dạng cream có dưỡng ẩm\n\n💡 Mẹo:\n• Rửa mặt với nước ấm, không nước nóng\n• Đắp mặt nạ dưỡng ẩm 2–3 lần/tuần\n• Dùng kem dưỡng khi da còn hơi ẩm sau toner',
        suggestProductIds: [],
        priority: 4,
        isActive: true
    },

    // ── INGREDIENT ────────────────────────────────────────────────────────────

    {
        intentCode:  'ingredient_serum_moisturizer_combine',
        category:    'ingredient',
        question:    'Can I mix serum and moisturizer together?',
        aliases: [
            'có thể dùng serum và kem dưỡng cùng nhau không',
            'trộn serum với kem dưỡng',
            'serum kết hợp kem dưỡng',
            'mix serum and moisturizer'
        ],
        keywords: ['serum', 'kem dưỡng', 'moisturizer', 'kết hợp', 'combine', 'trộn', 'mix', 'cùng nhau', 'together'],
        answerText: 'Bạn không nên trộn serum và kem dưỡng trực tiếp với nhau. Thay vào đó, hãy dùng theo thứ tự:\n\n1. Bôi serum lên da\n2. Đợi 30–60 giây để thẩm thấu\n3. Bôi kem dưỡng ẩm lên trên\n\nTại sao không trộn?\n• Serum và kem dưỡng có công thức khác nhau — trộn có thể phá vỡ kết cấu\n• Trộn trực tiếp làm giảm hiệu quả của cả hai sản phẩm\n• Một số thành phần có thể phản ứng khi tiếp xúc trực tiếp\n\nDùng riêng theo thứ tự là cách hiệu quả nhất.',
        suggestProductIds: [],
        priority: 5,
        isActive: true
    },
    {
        intentCode:  'ingredient_niacinamide_vitamin_c',
        category:    'ingredient',
        question:    'Can I use niacinamide and vitamin C together?',
        aliases: [
            'dùng niacinamide và vitamin c cùng nhau',
            'niacinamide với vitamin c',
            'có thể kết hợp niacinamide vitamin c',
            'niacinamide and vitamin c together'
        ],
        keywords: ['niacinamide', 'vitamin c', 'kết hợp', 'combine', 'cùng nhau', 'together'],
        answerText: 'Niacinamide và Vitamin C có thể dùng cùng nhau với công thức hiện đại, nhưng cần lưu ý:\n\n✅ An toàn khi:\n• Nồng độ Vitamin C thấp (< 10%)\n• Da đã quen với cả hai thành phần\n\n⚠️ Lưu ý:\n• Vitamin C nồng độ cao (>20%) + Niacinamide có thể gây đỏ/kích ứng ở da nhạy cảm\n\n💡 Khuyến nghị nếu lo ngại:\n• Dùng Vitamin C buổi sáng, Niacinamide buổi tối\n• Hoặc cách nhau 15–20 phút',
        suggestProductIds: [],
        priority: 6,
        isActive: true
    },
    {
        intentCode:  'ingredient_retinol_safe',
        category:    'ingredient',
        question:    'How do I use retinol safely?',
        aliases: [
            'cách dùng retinol an toàn',
            'retinol có an toàn không',
            'dùng retinol như thế nào',
            'retinol usage guide'
        ],
        keywords: ['retinol', 'retinoid', 'an toàn', 'safe', 'kích ứng', 'irritation', 'cách dùng', 'peeling'],
        answerText: 'Hướng dẫn dùng Retinol an toàn:\n\n📅 Bắt đầu từ từ:\n• Tuần 1–2: Dùng 1 lần/tuần\n• Tuần 3–4: Tăng lên 2–3 lần/tuần\n• Sau 1 tháng: Có thể dùng hàng ngày (nếu da chịu được)\n\n🌙 Chỉ dùng buổi tối\n\n⚠️ Không kết hợp:\n• AHA/BHA cùng lúc (dễ kích ứng)\n• Vitamin C nồng độ cao\n• Benzoyl peroxide\n\n✅ Luôn dùng kèm:\n• Kem dưỡng ẩm (giảm kích ứng)\n• Kem chống nắng SPF50+ ban ngày\n\n❌ Không dùng khi: mang thai, cho con bú, da đang tổn thương.',
        suggestProductIds: [],
        priority: 7,
        isActive: true
    },
    {
        intentCode:  'ingredient_aha_bha_difference',
        category:    'ingredient',
        question:    'What is the difference between AHA and BHA?',
        aliases: [
            'aha và bha khác nhau như thế nào',
            'aha vs bha',
            'difference between aha and bha',
            'nên dùng aha hay bha'
        ],
        keywords: ['aha', 'bha', 'acid', 'exfoliate', 'tẩy tế bào chết', 'khác nhau', 'difference'],
        answerText: 'AHA và BHA đều là acid tẩy tế bào chết, nhưng khác nhau:\n\nAHA (Alpha Hydroxy Acid — glycolic, lactic acid):\n• Tan trong nước, tẩy da trên bề mặt\n• Phù hợp da khô, thiếu ẩm, nếp nhăn\n• Giúp đều màu da, mờ thâm\n\nBHA (Beta Hydroxy Acid — salicylic acid):\n• Tan trong dầu, thấm sâu vào lỗ chân lông\n• Phù hợp da dầu, mụn đầu đen, lỗ chân lông to\n• Kháng khuẩn nhẹ\n\n💡 Gợi ý:\n• Da dầu/mụn → BHA\n• Da khô/thâm → AHA\n• Da hỗn hợp → PHA hoặc kết hợp',
        suggestProductIds: [],
        priority: 8,
        isActive: true
    },

    // ── PRODUCT ───────────────────────────────────────────────────────────────

    {
        intentCode:  'product_recommend_cushion',
        category:    'product',
        question:    'Which TirTir cushion should I choose?',
        aliases: [
            'tirtir cushion nào tốt',
            'nên mua cushion nào',
            'cushion tirtir phù hợp',
            'which tirtir cushion'
        ],
        keywords: ['cushion', 'phấn nước', 'tirtir', 'nên mua', 'gợi ý', 'recommend', 'phù hợp', 'phấn'],
        answerText: 'TIRTIR có hai dòng cushion phổ biến:\n\n🔴 Mask Fit Red Cushion\n• Độ phủ cao, che phủ khuyết điểm tốt\n• Phù hợp: da dầu/hỗn hợp\n• Texture nhẹ, lâu trôi\n\n🌸 Blur Fit Cushion\n• Độ phủ vừa, tự nhiên\n• Phù hợp: da khô/thường, muốn vẻ tươi tắn\n• Hiệu ứng làm mờ lỗ chân lông\n\n💡 Gợi ý:\n• Da dầu → Mask Fit Red Cushion\n• Da khô → Blur Fit Cushion\n• Da nhạy cảm → Thử sample trước khi mua',
        suggestProductIds: [],
        priority: 9,
        isActive: true
    },
    {
        intentCode:  'product_sunscreen_recommend',
        category:    'product',
        question:    'What sunscreen does TirTir recommend?',
        aliases: [
            'kem chống nắng tirtir',
            'tirtir sunscreen',
            'nên dùng kem chống nắng nào',
            'best sunscreen tirtir'
        ],
        keywords: ['kem chống nắng', 'sunscreen', 'spf', 'tirtir', 'chống nắng', 'uva', 'uvb'],
        answerText: 'Kem chống nắng là bước bắt buộc trong mọi routine. TIRTIR khuyến nghị:\n\n• SPF 30+ cho mọi ngày\n• SPF 50+ khi hoạt động ngoài trời\n\n🔑 Tiêu chí chọn kem chống nắng:\n• Da dầu: dạng fluid/gel, finish matte\n• Da khô: dạng cream có dưỡng ẩm\n• Da nhạy cảm: chứa mineral filter (zinc oxide)\n\nBạn muốn xem chi tiết sản phẩm kem chống nắng trong shop không?',
        suggestProductIds: [],
        priority: 10,
        isActive: true
    },

    // ── ORDER ─────────────────────────────────────────────────────────────────

    {
        intentCode:  'order_check_status',
        category:    'order',
        question:    'How can I check my order status?',
        aliases: [
            'kiểm tra trạng thái đơn hàng',
            'đơn hàng của tôi đang ở đâu',
            'check order status',
            'track my order',
            'theo dõi đơn hàng',
            'how to check my order'
        ],
        keywords: ['đơn hàng', 'order', 'trạng thái', 'status', 'kiểm tra', 'check', 'theo dõi', 'track', 'giao hàng', 'delivery', 'shipping'],
        answerText: 'Để kiểm tra trạng thái đơn hàng:\n\n📱 Trong ứng dụng TirTir:\n1. Vào tab "Tài khoản" → "Đơn hàng của tôi"\n2. Chọn đơn hàng cần kiểm tra\n3. Xem trạng thái và thông tin vận chuyển\n\n📦 Các trạng thái:\n• Chờ xác nhận → Đang xử lý → Đóng gói\n• Đã giao vận chuyển → Đang giao → Đã giao\n\nNếu đơn hàng chậm trễ hoặc có vấn đề, hãy liên hệ nhân viên hỗ trợ để được giải quyết ngay.',
        suggestProductIds: [],
        priority: 11,
        isActive: true
    },
    {
        intentCode:  'order_cancel',
        category:    'order',
        question:    'How do I cancel an order?',
        aliases: [
            'hủy đơn hàng',
            'cancel order',
            'tôi muốn hủy đơn',
            'làm sao để hủy đơn',
            'how to cancel my order'
        ],
        keywords: ['hủy', 'cancel', 'đơn hàng', 'order', 'refund', 'hoàn tiền', 'hủy đơn'],
        answerText: 'Để hủy đơn hàng:\n\n✅ Có thể hủy khi đơn chưa được đóng gói.\n\n📱 Cách hủy trong app:\n1. "Tài khoản" → "Đơn hàng của tôi"\n2. Chọn đơn muốn hủy\n3. Nhấn "Hủy đơn" và chọn lý do\n\n❌ Không thể hủy khi đơn đã bàn giao vận chuyển.\n\nNếu không hủy được trong app, liên hệ hotline hoặc nhân viên hỗ trợ ngay.',
        suggestProductIds: [],
        priority: 12,
        isActive: true
    },
    {
        intentCode:  'order_return_refund',
        category:    'order',
        question:    'What is the return and refund policy?',
        aliases: [
            'chính sách đổi trả',
            'hoàn tiền như thế nào',
            'return policy',
            'refund policy',
            'tôi muốn đổi trả sản phẩm'
        ],
        keywords: ['đổi trả', 'hoàn tiền', 'return', 'refund', 'chính sách', 'policy', 'lỗi sản phẩm', 'bảo hành'],
        answerText: 'Chính sách đổi trả của TIRTIR:\n\n✅ Được đổi trả trong 7 ngày nếu:\n• Sản phẩm bị lỗi từ nhà sản xuất\n• Không đúng mô tả hoặc bị hư khi vận chuyển\n\n❌ Không đổi trả khi:\n• Sản phẩm đã sử dụng (trừ lỗi)\n• Quá 7 ngày kể từ khi nhận hàng\n• Sản phẩm khuyến mãi có ghi "không đổi trả"\n\n📞 Quy trình:\n1. Chụp ảnh sản phẩm lỗi\n2. Liên hệ hotline hoặc nhắn tin hỗ trợ\n3. Nhân viên xử lý trong 1–3 ngày làm việc',
        suggestProductIds: [],
        priority: 13,
        isActive: true
    },

    // ── CART & WISHLIST ───────────────────────────────────────────────────────

    {
        intentCode:  'cart_wishlist_save_for_later',
        category:    'cart_wishlist',
        question:    'How do I save products to my wishlist?',
        aliases: [
            'lưu sản phẩm vào wishlist',
            'thêm vào danh sách yêu thích',
            'save to wishlist',
            'cách dùng wishlist'
        ],
        keywords: ['wishlist', 'yêu thích', 'save', 'lưu', 'danh sách', 'list', 'heart', 'tim'],
        answerText: 'Cách lưu sản phẩm vào Wishlist:\n\n❤️ Từ trang sản phẩm:\n1. Mở trang chi tiết sản phẩm\n2. Nhấn icon trái tim ở góc phải\n3. Sản phẩm được lưu vào Wishlist\n\n📂 Xem Wishlist:\n• Vào tab "Tài khoản" → "Danh sách yêu thích"\n\n💡 Dùng Wishlist để theo dõi sản phẩm và dễ dàng thêm vào giỏ hàng sau.',
        suggestProductIds: [],
        priority: 14,
        isActive: true
    },

    // ── PROMOTION & COMBO ─────────────────────────────────────────────────────

    {
        intentCode:  'promotion_current_vouchers',
        category:    'promotion_combo',
        question:    'What vouchers or promotions are currently available?',
        aliases: [
            'có mã giảm giá nào không',
            'khuyến mãi hiện tại',
            'voucher tirtir',
            'promo code',
            'giảm giá hôm nay'
        ],
        keywords: ['khuyến mãi', 'giảm giá', 'voucher', 'promo', 'discount', 'mã', 'code', 'ưu đãi', 'sale', 'combo'],
        answerText: 'Để xem khuyến mãi và mã giảm giá hiện có:\n\n📱 Trong ứng dụng:\n1. Vào tab "Khuyến mãi" hoặc "Ưu đãi"\n2. Xem combo sản phẩm và mã giảm giá\n3. Tại trang thanh toán, nhập mã vào ô voucher\n\n🎁 Các loại ưu đãi thường có:\n• Mã giảm % cho đơn hàng đầu tiên\n• Combo 2–3 sản phẩm giá tốt hơn\n• Flash sale theo ngày/tuần\n• Ưu đãi cho thành viên loyalty\n\nNếu mã không áp dụng được, liên hệ hotline để được hỗ trợ.',
        suggestProductIds: [],
        priority: 15,
        isActive: true
    },

    // ── AI SCAN ───────────────────────────────────────────────────────────────

    {
        intentCode:  'ai_scan_how_to_use',
        category:    'ai_scan',
        question:    'How do I use the AI skin analysis feature?',
        aliases: [
            'cách dùng tính năng phân tích da',
            'ai scan da',
            'phân tích da bằng ai',
            'how to use skin analysis',
            'tính năng ai scan',
            'hướng dẫn dùng ai phân tích da'
        ],
        keywords: ['ai', 'scan', 'phân tích da', 'skin analysis', 'camera', 'selfie', 'tính năng', 'chụp'],
        answerText: 'Cách sử dụng tính năng AI Phân tích Da của TIRTIR:\n\n📸 Các bước:\n1. Mở ứng dụng → Vào tab "AI"\n2. Chụp ảnh selfie hoặc chọn từ thư viện\n3. Giữ camera thẳng, ánh sáng tự nhiên\n4. Nhấn "Phân tích" và chờ kết quả\n\n📊 Kết quả bao gồm:\n• Loại da (dầu/khô/hỗn hợp/nhạy cảm)\n• Chỉ số độ ẩm, tông màu da\n• Gợi ý sản phẩm phù hợp\n• Routine cá nhân hóa\n\n💡 Để kết quả chính xác:\n• Chụp khi da sạch, không trang điểm\n• Dùng ánh sáng tự nhiên ban ngày\n• Không mang kính hoặc phụ kiện che mặt',
        suggestProductIds: [],
        priority: 16,
        isActive: true
    },

    // ── ACCOUNT ───────────────────────────────────────────────────────────────

    {
        intentCode:  'account_update_profile',
        category:    'account',
        question:    'How do I update my profile or skin information?',
        aliases: [
            'cập nhật hồ sơ',
            'update profile',
            'thay đổi thông tin cá nhân',
            'sửa thông tin',
            'cập nhật loại da'
        ],
        keywords: ['hồ sơ', 'profile', 'cập nhật', 'update', 'thông tin', 'information', 'loại da', 'skin type', 'mật khẩu', 'password'],
        answerText: 'Cách cập nhật thông tin tài khoản:\n\n👤 Thông tin cá nhân:\n1. Vào tab "Tài khoản"\n2. Nhấn vào tên hoặc ảnh đại diện\n3. Chọn "Chỉnh sửa hồ sơ"\n4. Cập nhật và lưu\n\n🧴 Cập nhật loại da:\n1. "Tài khoản" → "Hồ sơ da"\n2. Chọn loại da và các vấn đề da\n3. Lưu để nhận gợi ý sản phẩm phù hợp hơn\n\n🔑 Đổi mật khẩu:\n1. "Tài khoản" → "Bảo mật"\n2. Nhập mật khẩu cũ và mới\n3. Xác nhận và lưu',
        suggestProductIds: [],
        priority: 17,
        isActive: true
    },

    // ── HOTLINE & SUPPORT ─────────────────────────────────────────────────────

    {
        intentCode:  'hotline_contact_staff',
        category:    'hotline_support',
        question:    'How can I contact TirTir customer support?',
        aliases: [
            'liên hệ hỗ trợ',
            'hotline tirtir',
            'contact support',
            'gặp nhân viên',
            'tôi muốn nói chuyện với người',
            'speak to human',
            'talk to staff',
            'contact customer service'
        ],
        keywords: ['liên hệ', 'hotline', 'hỗ trợ', 'support', 'nhân viên', 'staff', 'tư vấn viên', 'human', 'người thật', 'contact'],
        answerText: 'Để liên hệ đội ngũ hỗ trợ TIRTIR:\n\n📞 Hotline: Nhấn nút "Gọi Hotline" để kết nối trực tiếp\n\n💬 Chat với nhân viên:\n• Nhấn "Nhắn tin với nhân viên" để được hỗ trợ trực tiếp\n• Giờ hỗ trợ: 8:00–22:00 hàng ngày\n\nNhân viên TIRTIR sẽ phản hồi trong vòng 30 phút trong giờ làm việc.',
        suggestProductIds: [],
        priority: 18,
        isActive: true
    }
];

// ── New Q&A entries (12 additional) ───────────────────────────────────────────

const newQuestions = [
    {
        intentCode: 'routine_night_order',
        category: 'routine',
        question: 'What is the correct night skincare routine?',
        aliases: ['thứ tự skincare ban đêm', 'night skincare routine', 'routine buổi tối', 'các bước dưỡng da buổi tối', 'chăm sóc da ban đêm'],
        keywords: ['buổi tối', 'night', 'thứ tự', 'routine', 'bước', 'chăm sóc da', 'skincare', 'tối', 'ngủ'],
        answerText: 'Thứ tự skincare buổi tối chuẩn:\n\n1. Tẩy trang (dầu tẩy trang hoặc balm)\n2. Rửa mặt sạch lần 2 (double cleanse)\n3. Toner / nước cân bằng da\n4. Essence (nếu dùng)\n5. Serum điều trị (retinol, niacinamide, v.v.)\n6. Kem mắt\n7. Kem dưỡng ẩm ban đêm (night cream)\n8. Dầu dưỡng (nếu da khô — bước cuối cùng)\n\n💡 Tip: Buổi tối là lúc da hồi phục và tái tạo. Dùng serum điều trị mạnh hơn (retinol, AHA/BHA) vào ban đêm để tránh nhạy cảm với ánh sáng.',
        suggestProductIds: [],
        priority: 19,
        isActive: true
    },
    {
        intentCode: 'routine_combination_skin',
        category: 'routine',
        question: 'What is a good routine for combination skin?',
        aliases: ['da hỗn hợp nên dùng gì', 'routine da hỗn hợp', 'chăm sóc da hỗn hợp', 'combination skin routine'],
        keywords: ['da hỗn hợp', 'combination', 'hỗn hợp', 'zone t', 't-zone', 'vùng chữ t'],
        answerText: 'Routine cho da hỗn hợp (vùng T dầu, vùng má khô):\n\n✅ Nguyên tắc: Cân bằng độ ẩm cho toàn mặt, kiểm soát dầu vùng T.\n\n📋 Routine gợi ý:\n1. Sữa rửa mặt gel nhẹ (cân bằng dầu/nước)\n2. Toner cấp nước nhẹ (không cồn)\n3. Serum niacinamide (điều tiết bã nhờn toàn mặt)\n4. Kem dưỡng ẩm dạng lotion (không quá nặng)\n5. Kem chống nắng fluid (không gây bít lỗ chân lông)\n\n🎯 Tip:\n• Dùng clay mask 1–2 lần/tuần chỉ ở vùng T\n• Tránh sản phẩm quá giàu dầu ở vùng trán và mũi',
        suggestProductIds: [],
        priority: 20,
        isActive: true
    },
    {
        intentCode: 'routine_sensitive_skin',
        category: 'routine',
        question: 'What skincare routine is safe for sensitive skin?',
        aliases: ['da nhạy cảm nên dùng gì', 'routine da nhạy cảm', 'chăm sóc da nhạy cảm', 'sensitive skin routine'],
        keywords: ['da nhạy cảm', 'sensitive', 'kích ứng', 'đỏ da', 'mẩn', 'dị ứng', 'fragrance-free', 'không mùi'],
        answerText: 'Routine an toàn cho da nhạy cảm:\n\n✅ Nguyên tắc: Đơn giản, dịu nhẹ, ít thành phần.\n\n📋 Routine tối giản:\n1. Sữa rửa mặt không SLS, không hương liệu\n2. Toner cấp nước nhẹ (có centella asiatica hoặc aloe vera)\n3. Serum dưỡng ẩm (hyaluronic acid)\n4. Kem dưỡng dịu nhẹ có ceramide\n5. Kem chống nắng mineral (zinc oxide) — không hóa học\n\n❌ Tránh:\n• SLS, alcohol, hương liệu tổng hợp\n• AHA/BHA nồng độ cao (bắt đầu thấp nếu cần)\n• Retinol khi da đang kích ứng\n\n💡 Luôn patch test sản phẩm mới trước khi dùng toàn mặt.',
        suggestProductIds: [],
        priority: 21,
        isActive: true
    },
    {
        intentCode: 'product_rec_skin_type',
        category: 'product',
        question: 'Can you recommend products for my skin type?',
        aliases: ['gợi ý sản phẩm theo loại da', 'sản phẩm phù hợp loại da', 'recommend products for skin type', 'products by skin type'],
        keywords: ['gợi ý sản phẩm', 'loại da', 'skin type', 'recommend', 'phù hợp', 'da dầu', 'da khô', 'da thường', 'da hỗn hợp'],
        answerText: 'Để tư vấn sản phẩm phù hợp, TIRTIR có phân loại theo từng loại da:\n\n🛢️ Da dầu: Sản phẩm oil-free, kiểm soát nhờn, niacinamide\n💧 Da khô: Sản phẩm giàu dưỡng ẩm, ceramide, hyaluronic acid\n🔄 Da hỗn hợp: Sản phẩm cân bằng, gel-cream, nhẹ dịu\n🌿 Da nhạy cảm: Sản phẩm fragrance-free, mineral filter, centella\n\nBạn muốn xem gợi ý cho loại da nào? Tôi có thể hướng dẫn bạn vào đúng danh mục sản phẩm trong app.\n\nHoặc dùng tính năng AI Scan để phân tích loại da chính xác nhất!',
        suggestProductIds: [],
        priority: 22,
        isActive: true
    },
    {
        intentCode: 'product_rec_concern',
        category: 'product',
        question: 'What products help with my skin concern?',
        aliases: ['sản phẩm trị mụn', 'sản phẩm làm trắng', 'sản phẩm chống lão hóa', 'products for acne', 'products for dark spots', 'skin concern'],
        keywords: ['mụn', 'acne', 'thâm', 'dark spot', 'nếp nhăn', 'wrinkle', 'chống lão hóa', 'anti-aging', 'lỗ chân lông', 'skin concern', 'vấn đề da'],
        answerText: 'TIRTIR có sản phẩm giải quyết nhiều vấn đề da:\n\n🎯 Mụn & lỗ chân lông: BHA serum, toner salicylic acid, kem nền không gây mụn\n🌟 Thâm mảng & không đều màu: Vitamin C serum, niacinamide, toner làm sáng\n⏰ Lão hóa & nếp nhăn: Retinol (ban đêm), peptide serum, night cream\n💦 Thiếu ẩm & mất nước: Hyaluronic acid, sleeping mask, essence\n\nBạn đang gặp vấn đề gì cụ thể? Tôi có thể tư vấn sản phẩm phù hợp hoặc bạn có thể vào Shop để lọc theo danh mục.',
        suggestProductIds: [],
        priority: 23,
        isActive: true
    },
    {
        intentCode: 'product_toner_essence',
        category: 'product',
        question: 'What is the difference between toner and essence?',
        aliases: ['toner khác essence như thế nào', 'toner vs essence', 'dùng toner hay essence', 'toner and essence difference', 'essence là gì'],
        keywords: ['toner', 'essence', 'khác nhau', 'difference', 'nước cân bằng', 'dưỡng chất', 'thứ tự'],
        answerText: 'Toner và Essence đều là bước cấp nước/dưỡng chất, nhưng khác nhau:\n\n💧 Toner:\n• Bước đầu sau rửa mặt\n• Cân bằng độ pH da\n• Cấp nước nhẹ, chuẩn bị da hấp thu bước tiếp\n• Texture: loãng như nước\n\n✨ Essence:\n• Bước sau toner\n• Nồng độ dưỡng chất cao hơn\n• Hỗ trợ tái tạo da, cấp ẩm sâu hơn\n• Texture: sánh hơn toner, loãng hơn serum\n\n📋 Thứ tự: Toner → Essence → Serum → Kem dưỡng\n\nNếu da bạn không quá nhiều vấn đề, toner + serum đã đủ, không nhất thiết phải dùng cả 3.',
        suggestProductIds: [],
        priority: 24,
        isActive: true
    },
    {
        intentCode: 'ingredient_sensitive_skin',
        category: 'ingredient',
        question: 'What ingredients are safe for sensitive skin?',
        aliases: ['thành phần an toàn cho da nhạy cảm', 'ingredients for sensitive skin', 'da nhạy cảm nên tránh gì', 'safe ingredients sensitive'],
        keywords: ['thành phần', 'ingredient', 'da nhạy cảm', 'sensitive', 'an toàn', 'safe', 'tránh', 'avoid'],
        answerText: 'Thành phần AN TOÀN cho da nhạy cảm:\n\n✅ Nên tìm kiếm:\n• Centella Asiatica (trực tiếp làm dịu)\n• Ceramide (phục hồi hàng rào da)\n• Hyaluronic Acid (cấp nước không kích ứng)\n• Aloe Vera (làm dịu, kháng viêm nhẹ)\n• Zinc Oxide (kem chống nắng vật lý, nhẹ nhàng)\n• Allantoin (làm mềm, phục hồi da)\n\n❌ CẦN TRÁNH:\n• SLS/SLES (sulfate)\n• Hương liệu tổng hợp (fragrance)\n• Alcohol nồng độ cao (denatured alcohol)\n• Essential oils (có thể gây kích ứng)\n• AHA/BHA nồng độ cao khi da đang yếu\n• Retinol khi mới bắt đầu',
        suggestProductIds: [],
        priority: 25,
        isActive: true
    },
    {
        intentCode: 'ingredient_acne_safe',
        category: 'ingredient',
        question: 'What ingredients are good for acne-prone skin?',
        aliases: ['thành phần trị mụn', 'ingredients for acne', 'da mụn nên dùng gì', 'acne-safe ingredients', 'nguyên liệu trị mụn'],
        keywords: ['mụn', 'acne', 'thành phần', 'ingredient', 'comedogenic', 'trị mụn', 'bít lỗ chân lông'],
        answerText: 'Thành phần tốt cho da mụn:\n\n✅ Hiệu quả trị mụn:\n• Salicylic Acid BHA 0.5–2% (thông lỗ chân lông)\n• Niacinamide 5–10% (giảm bã nhờn, làm dịu đỏ)\n• Benzoyl Peroxide 2.5–5% (diệt khuẩn mụn)\n• Tea Tree Oil 5% (kháng khuẩn tự nhiên)\n• Azelaic Acid (giảm viêm và thâm mụn)\n• Zinc (kiểm soát dầu)\n\n❌ Tránh thành phần comedogenic:\n• Dầu dừa (coconut oil) — gây bít lỗ\n• Lanolin — gây mụn đầu đen\n• Isopropyl Myristate — bít lỗ chân lông\n• Silicone nặng (dimethicone ít gây mụn hơn)\n\n💡 Kiểm tra thành phần tại CosDNA hoặc INCI Decoder trước khi mua.',
        suggestProductIds: [],
        priority: 26,
        isActive: true
    },
    {
        intentCode: 'order_delivery_time',
        category: 'order',
        question: 'How long does delivery take?',
        aliases: ['giao hàng bao lâu', 'thời gian giao hàng', 'delivery time', 'khi nào nhận được hàng', 'how long shipping'],
        keywords: ['giao hàng', 'delivery', 'shipping', 'thời gian', 'time', 'bao lâu', 'how long', 'vận chuyển'],
        answerText: 'Thời gian giao hàng của TIRTIR:\n\n📦 Nội thành TP.HCM & Hà Nội: 1–2 ngày làm việc\n🚚 Tỉnh thành khác: 3–5 ngày làm việc\n✈️ Vùng xa/đảo: 5–7 ngày làm việc\n\n⏰ Lưu ý:\n• Đơn đặt trước 14:00 được xử lý trong ngày\n• Thứ 7, Chủ nhật và ngày lễ có thể chậm hơn 1–2 ngày\n• Flash sale và dịp lễ có thể ảnh hưởng thời gian giao\n\nSau khi đơn được giao cho vận chuyển, bạn sẽ nhận mã theo dõi trong ứng dụng.',
        suggestProductIds: [],
        priority: 27,
        isActive: true
    },
    {
        intentCode: 'ai_scan_results',
        category: 'ai_scan',
        question: 'What do my AI scan results mean?',
        aliases: ['kết quả ai scan có nghĩa gì', 'hiểu kết quả phân tích da', 'ai scan results meaning', 'đọc kết quả ai'],
        keywords: ['kết quả', 'result', 'ai scan', 'phân tích', 'ý nghĩa', 'meaning', 'chỉ số', 'score'],
        answerText: 'Cách đọc kết quả AI Phân tích Da:\n\n🔬 Các chỉ số phân tích:\n• Loại da: Dầu / Khô / Hỗn hợp / Thường / Nhạy cảm\n• Độ ẩm (Hydration score): 0–100 — dưới 50 là thiếu ẩm\n• Nhờn (Sebum level): cao ở da dầu/hỗn hợp\n• Độ đều màu: Điểm thâm, nám, không đều tông\n• Wrinkle score: Đánh giá nếp nhăn và lão hóa\n\n💡 Dựa trên kết quả:\n• App gợi ý routine phù hợp loại da của bạn\n• Gợi ý sản phẩm TIRTIR giải quyết đúng vấn đề\n• Bạn có thể tái phân tích mỗi 2–4 tuần để theo dõi tiến độ\n\nKết quả chỉ mang tính tham khảo. Để chẩn đoán chính xác, hãy tham khảo chuyên gia da liễu.',
        suggestProductIds: [],
        priority: 28,
        isActive: true
    },
    {
        intentCode: 'promo_combo_sets',
        category: 'promotion_combo',
        question: 'What combo sets or bundles does TirTir offer?',
        aliases: ['combo sản phẩm tirtir', 'bộ sản phẩm', 'skincare set', 'bundle tirtir', 'gói combo'],
        keywords: ['combo', 'set', 'bộ sản phẩm', 'bundle', 'gói', 'routine set', 'starter kit'],
        answerText: 'TIRTIR cung cấp các combo và bộ sản phẩm tiết kiệm:\n\n🎁 Các loại combo phổ biến:\n• Starter Kit: Toner + Serum cơ bản (cho người mới bắt đầu)\n• Cushion + Cushion Refill: Tiết kiệm hơn mua lẻ\n• Skincare Routine Set: Đủ 5 bước trong 1 hộp\n• Limited Edition Gift Set: Theo mùa/dịp lễ\n\n💰 Lợi ích combo:\n• Giá tốt hơn mua từng sản phẩm riêng lẻ 10–20%\n• Đảm bảo sản phẩm tương thích nhau\n• Bao bì đẹp — phù hợp làm quà\n\nXem tất cả combo hiện tại trong tab "Khuyến mãi" của ứng dụng.',
        suggestProductIds: [],
        priority: 29,
        isActive: true
    },
    {
        intentCode: 'account_loyalty_points',
        category: 'account',
        question: 'How do loyalty points work?',
        aliases: ['điểm tích lũy là gì', 'loyalty points', 'điểm thưởng', 'cách dùng điểm tích lũy', 'how do points work'],
        keywords: ['điểm tích lũy', 'loyalty', 'điểm thưởng', 'points', 'reward', 'tích điểm', 'đổi điểm'],
        answerText: 'Chương trình Điểm Tích Lũy (Loyalty Points) của TIRTIR:\n\n💎 Cách tích điểm:\n• Mua hàng: 1.000đ = 1 điểm\n• Đánh giá sản phẩm sau mua: +50 điểm\n• Sinh nhật: +200 điểm bonus\n• Giới thiệu bạn bè: +100 điểm/người\n\n🎁 Đổi điểm:\n• 100 điểm = 10.000đ giảm giá\n• Đổi quà tặng trong mục "Phần thưởng"\n• Nâng cấp thành viên (Silver, Gold, Platinum)\n\n📱 Xem điểm của bạn:\n• Vào tab "Tài khoản" → "Điểm tích lũy"\n• Xem lịch sử tích điểm và đổi điểm\n\nĐiểm có hiệu lực 12 tháng kể từ ngày tích.',
        suggestProductIds: [],
        priority: 30,
        isActive: true
    }
];

// ── Chat Categories ───────────────────────────────────────────────────────────

const chatCategories = [
    // ── Level 1: Root topics ──────────────────────────────────────────────
    { id: 'cat_routine',    title: 'Skincare Routine',        emoji: '🌿', parentId: null, level: 1, sortOrder: 1, isLeaf: false, intentCode: null, isActive: true },
    { id: 'cat_products',   title: 'Product Recommendation',  emoji: '✨', parentId: null, level: 1, sortOrder: 2, isLeaf: false, intentCode: null, isActive: true },
    { id: 'cat_ingredient', title: 'Ingredient Safety',       emoji: '🔬', parentId: null, level: 1, sortOrder: 3, isLeaf: false, intentCode: null, isActive: true },
    { id: 'cat_promo',      title: 'Promotions & Combos',     emoji: '🎁', parentId: null, level: 1, sortOrder: 4, isLeaf: false, intentCode: null, isActive: true },
    { id: 'cat_order',      title: 'Order Support',           emoji: '📦', parentId: null, level: 1, sortOrder: 5, isLeaf: false, intentCode: null, isActive: true },
    { id: 'cat_ai_scan',    title: 'AI Skin Scan',            emoji: '📸', parentId: null, level: 1, sortOrder: 6, isLeaf: false, intentCode: null, isActive: true },
    { id: 'cat_account',    title: 'Account & Loyalty',       emoji: '👤', parentId: null, level: 1, sortOrder: 7, isLeaf: false, intentCode: null, isActive: true },

    // ── Level 2: Skincare Routine children ────────────────────────────────
    { id: 'cat_routine_morning', title: 'Morning Routine',          parentId: 'cat_routine', level: 2, sortOrder: 1, isLeaf: true, intentCode: 'routine_morning_order',           isActive: true },
    { id: 'cat_routine_night',   title: 'Night Routine',            parentId: 'cat_routine', level: 2, sortOrder: 2, isLeaf: true, intentCode: 'routine_night_order',             isActive: true },
    { id: 'cat_routine_serum',   title: 'Serum vs Moisturizer',    parentId: 'cat_routine', level: 2, sortOrder: 3, isLeaf: true, intentCode: 'routine_serum_before_moisturizer', isActive: true },
    { id: 'cat_routine_oily',    title: 'Oily Skin Routine',        parentId: 'cat_routine', level: 2, sortOrder: 4, isLeaf: true, intentCode: 'routine_for_oily_skin',           isActive: true },
    { id: 'cat_routine_dry',     title: 'Dry Skin Routine',         parentId: 'cat_routine', level: 2, sortOrder: 5, isLeaf: true, intentCode: 'routine_for_dry_skin',            isActive: true },
    { id: 'cat_routine_combo',   title: 'Combination Skin',         parentId: 'cat_routine', level: 2, sortOrder: 6, isLeaf: true, intentCode: 'routine_combination_skin',        isActive: true },
    { id: 'cat_routine_sens',    title: 'Sensitive Skin',           parentId: 'cat_routine', level: 2, sortOrder: 7, isLeaf: true, intentCode: 'routine_sensitive_skin',          isActive: true },

    // ── Level 2: Product Recommendation children ──────────────────────────
    { id: 'cat_prod_type',    title: 'By Skin Type',        parentId: 'cat_products', level: 2, sortOrder: 1, isLeaf: true, intentCode: 'product_rec_skin_type',    isActive: true },
    { id: 'cat_prod_concern', title: 'By Skin Concern',     parentId: 'cat_products', level: 2, sortOrder: 2, isLeaf: true, intentCode: 'product_rec_concern',      isActive: true },
    { id: 'cat_prod_cushion', title: 'Cushion Foundation',  parentId: 'cat_products', level: 2, sortOrder: 3, isLeaf: true, intentCode: 'product_recommend_cushion', isActive: true },
    { id: 'cat_prod_toner',   title: 'Toner & Essence',     parentId: 'cat_products', level: 2, sortOrder: 4, isLeaf: true, intentCode: 'product_toner_essence',    isActive: true },

    // ── Level 2: Ingredient Safety children ───────────────────────────────
    { id: 'cat_ing_combine',  title: 'Combining Products',  parentId: 'cat_ingredient', level: 2, sortOrder: 1, isLeaf: true, intentCode: 'ingredient_serum_moisturizer_combine', isActive: true },
    { id: 'cat_ing_sensitive',title: 'Sensitive Skin Safe', parentId: 'cat_ingredient', level: 2, sortOrder: 2, isLeaf: true, intentCode: 'ingredient_sensitive_skin',            isActive: true },
    { id: 'cat_ing_acne',     title: 'Acne-Prone Skin',     parentId: 'cat_ingredient', level: 2, sortOrder: 3, isLeaf: true, intentCode: 'ingredient_acne_safe',                isActive: true },
    { id: 'cat_ing_spf',      title: 'SPF & Sunscreen',     parentId: 'cat_ingredient', level: 2, sortOrder: 4, isLeaf: true, intentCode: 'product_sunscreen_recommend',         isActive: true },

    // ── Level 2: Other categories children ───────────────────────────────
    { id: 'cat_promo_current',    title: 'Current Promotions',  parentId: 'cat_promo',    level: 2, sortOrder: 1, isLeaf: true, intentCode: 'promotion_current_vouchers', isActive: true },
    { id: 'cat_promo_combo',      title: 'Combo & Bundle Deals',parentId: 'cat_promo',    level: 2, sortOrder: 2, isLeaf: true, intentCode: 'promo_combo_sets',           isActive: true },
    { id: 'cat_order_status',     title: 'Check Order Status',  parentId: 'cat_order',    level: 2, sortOrder: 1, isLeaf: true, intentCode: 'order_check_status',         isActive: true },
    { id: 'cat_order_cancel',     title: 'Cancel an Order',     parentId: 'cat_order',    level: 2, sortOrder: 2, isLeaf: true, intentCode: 'order_cancel',               isActive: true },
    { id: 'cat_order_return',     title: 'Returns & Refunds',   parentId: 'cat_order',    level: 2, sortOrder: 3, isLeaf: true, intentCode: 'order_return_refund',        isActive: true },
    { id: 'cat_order_delivery',   title: 'Delivery Time',       parentId: 'cat_order',    level: 2, sortOrder: 4, isLeaf: true, intentCode: 'order_delivery_time',        isActive: true },
    { id: 'cat_ai_how',           title: 'How AI Scan Works',   parentId: 'cat_ai_scan',  level: 2, sortOrder: 1, isLeaf: true, intentCode: 'ai_scan_how_to_use',         isActive: true },
    { id: 'cat_ai_results',       title: 'Reading My Results',  parentId: 'cat_ai_scan',  level: 2, sortOrder: 2, isLeaf: true, intentCode: 'ai_scan_results',            isActive: true },
    { id: 'cat_acc_loyalty',      title: 'Loyalty Points',      parentId: 'cat_account',  level: 2, sortOrder: 1, isLeaf: true, intentCode: 'account_loyalty_points',     isActive: true },
    { id: 'cat_acc_profile',      title: 'Update My Profile',   parentId: 'cat_account',  level: 2, sortOrder: 2, isLeaf: true, intentCode: 'account_update_profile',     isActive: true },
];

// ── Seed function ─────────────────────────────────────────────────────────────

async function seed() {
    console.log('\n🌱 TirTir Chat Data Seeder\n');
    const db = initFirebase();

    // Seed chatConfig
    process.stdout.write('Writing chatConfig/default ... ');
    await db.collection('chatConfig').doc('default').set(chatConfig, { merge: true });
    console.log('✓');

    // Seed suggestedQuestions (existing 18 + new 12)
    const allQuestions = [...questions, ...newQuestions];
    console.log(`\nSeeding ${allQuestions.length} Q&A items:\n`);
    const batch = db.batch();
    const collRef = db.collection('suggestedQuestions');
    for (const q of allQuestions) {
        const docRef = collRef.doc();
        batch.set(docRef, { ...q, createdAt: new Date() });
        console.log(`  ✓ [${q.category.padEnd(15)}] ${q.intentCode}`);
    }
    await batch.commit();

    // Seed chatCategories (using fixed doc IDs)
    console.log(`\nSeeding ${chatCategories.length} chatCategories:\n`);
    const catBatch = db.batch();
    for (const cat of chatCategories) {
        const { id, ...data } = cat;
        const docRef = db.collection('chatCategories').doc(id);
        catBatch.set(docRef, { ...data, createdAt: new Date() });
        console.log(`  ✓ [L${data.level}] ${id} — "${data.title}"`);
    }
    await catBatch.commit();

    console.log(`\n✅ Done — ${questions.length + newQuestions.length} Q&A items + ${chatCategories.length} categories seeded.\n`);
    process.exit(0);
}

seed().catch(err => {
    console.error('\n❌ Seed failed:', err.message);
    process.exit(1);
});
