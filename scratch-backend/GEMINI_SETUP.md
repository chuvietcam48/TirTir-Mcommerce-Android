# 🔧 Setup Hướng Dẫn: Gemini AI cho Virtual Camera

## Bước 1: Lấy Gemini API Key (MIỄN PHÍ)

1. **Truy cập**: https://ai.google.dev/
2. Click nút **"Get API key in Google AI Studio"**
3. Đăng nhập bằng tài khoản Google
4. Click **"Create API Key"**
5. Copy API key (dạng: `AIzaSy...`)

> ⚠️ **LƯU Ý**: API key này MIỄN PHÍ cho development. Gemini có free tier rất hào phóng!

---

## Bước 2: Cấu hình Backend

1. Mở file: `d:\TirTir-Project\backend\.env`
2. Tìm dòng:
   ```
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
3. Thay bằng API key thật của bạn:
   ```
   GEMINI_API_KEY=AIzaSyABC123...your_actual_key
   ```
4. **Save file** (Ctrl+S)

---

## Bước 3: Restart Backend Server

1. Vào terminal backend (đang chạy `npm run dev`)
2. Nhấn **Ctrl+C** để stop
3. Chạy lại:
   ```bash
   npm run dev
   ```
4. Kiểm tra log, phải thấy:
   ```
   ✅ Gemini AI initialized successfully
   ```

> ❌ Nếu thấy: `⚠️ GEMINI_API_KEY is not configured properly!`
> → API key chưa đúng hoặc vẫn là placeholder

---

## Bước 4: Test AI Feature

1. Mở browser: http://localhost:4200/virtual-services
2. Click **"Start Camera"**
3. Allow camera permissions
4. Click nút **"🤖 AI Scan"** (màu tím)
5. Đợi 3-5 giây
6. Xem kết quả AI analysis!

---

## Troubleshooting

### Lỗi: "Could not connect to AI service"
**Nguyên nhân**: Backend chưa có API key hoặc chưa restart
**Giải pháp**: 
- Check file `.env` có API key thật chưa
- Restart backend server
- Check terminal log có `✅ Gemini AI initialized successfully` chưa

### Lỗi: "AI analysis failed"
**Nguyên nhân**: API key không hợp lệ hoặc hết quota
**Giải pháp**:
- Tạo API key mới tại https://ai.google.dev/
- Check quota tại Google AI Studio

### Backend không khởi động
**Giải pháp**: Check terminal backend có lỗi gì không

---

## Demo Flow

```
1. User click "AI Scan"
   ↓
2. Frontend capture ảnh từ camera (base64)
   ↓
3. Gửi đến Backend: POST /api/ai/analyze-skin
   ↓
4. Backend gọi Gemini Vision API
   ↓
5. Gemini phân tích và trả về JSON
   ↓
6. Frontend hiển thị kết quả với explanation bằng tiếng Việt!
```

Sau khi setup xong, bạn sẽ thấy AI thông minh đưa ra lời giải thích cá nhân hóa thay vì text hardcode! 🎉
