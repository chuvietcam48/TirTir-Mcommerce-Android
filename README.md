# TirTir M-Commerce — Android App

Ứng dụng thương mại điện tử mỹ phẩm TirTir trên nền tảng Android, kết nối với backend REST API đã được deploy sẵn.

---

## Live Demo & Links

| Thành phần | Link |
|---|---|
| **Backend API** | https://tirtir-project.onrender.com |
| **Web version (cũ)** | `[điền link web deploy vào đây — Vercel/Netlify]` |
| **API Health check** | https://tirtir-project.onrender.com/api/v1/products?limit=5 |

> **Lưu ý Render free-tier:** Backend ngủ sau 15 phút không có request. Lần đầu gọi API có thể mất 30–60 giây để wake up — app đã cấu hình timeout 60s để xử lý trường hợp này.

---

## Xem Demo nhanh bằng Appetize (không cần máy ảo)

Nếu máy không chạy được Android Emulator (RAM yếu, thiếu Hyper-V...) thì dùng Appetize.io:

1. Build APK debug: **Build → Build Bundle(s) / APK(s) → Build APK(s)** trong Android Studio  
   File APK nằm tại: `app/build/outputs/apk/debug/app-debug.apk`
2. Truy cập **https://appetize.io/upload**
3. Upload file `app-debug.apk` → nhận link demo online
4. Chia sẻ link cho người khác xem thử trực tiếp trên trình duyệt

---

## Tech Stack

- **Language:** Java (Android)
- **Min SDK:** 24 (Android 7.0) — Target SDK: 36
- **Architecture:** MVVM (ViewModel + LiveData + Repository)
- **Networking:** Retrofit 2 + OkHttp + Gson (JWT Auth Interceptor)
- **SOAP:** ksoap2-android 3.6.4 — `ViettelPostSoapClient` (real SOAP → auto-fallback stub)
- **Local DB:** SQLite — products cache + FTS4 ingredient_conflicts + ContentProvider (wishlist)
- **Firebase:** Firestore, Auth, Cloud Messaging, Storage, Analytics
- **Image loading:** Glide
- **UI:** Material Design 3, ConstraintLayout, BottomNavigationView

---

## Yêu cầu môi trường

| Công cụ | Phiên bản tối thiểu |
|---|---|
| Android Studio | Ladybug (2024.2.x) trở lên |
| JDK | 11 |
| Android SDK | API 24+ |
| Gradle | 9.1.1 (tự tải qua wrapper) |

---

## Hướng dẫn Setup sau khi Clone

### Bước 1 — Clone repo

```bash
git clone https://github.com/<your-username>/TirTir-Mcommerce.git
cd TirTir-Mcommerce
```

### Bước 2 — Mở project bằng Android Studio

- Chọn **File → Open** → chọn thư mục `TirTir-Mcommerce`
- Chờ Gradle sync (lần đầu sẽ tải dependencies, có thể mất vài phút)

### Bước 3 — Thêm `google-services.json` (Firebase)

File này **không được commit lên git** vì chứa thông tin nhạy cảm.

**Cách lấy:**
1. Truy cập [Firebase Console](https://console.firebase.google.com) → Project TirTir
2. Vào **Project Settings → General → Your apps → Android app**
3. Tải file `google-services.json`
4. Đặt file vào thư mục `app/` (ngang hàng với `build.gradle.kts`)

```
TirTir-Mcommerce/
└── app/
    ├── google-services.json   ← đặt vào đây
    ├── build.gradle.kts
    └── src/
```

> Nếu không có quyền truy cập Firebase Console, liên hệ team để được share file.

### Bước 4 — Deploy Firestore Security Rules (lần đầu)

File `firestore.rules` ở root project quy định quyền đọc/ghi Firestore.  
Nếu là người setup Firebase lần đầu, cần deploy rules:

```bash
# Cần Firebase CLI
npm install -g firebase-tools
firebase login
firebase deploy --only firestore:rules
```

> Các thành viên còn lại **không cần** bước này — chỉ cần 1 người deploy 1 lần.

### Bước 5 — Sync & Build

```
Android Studio: File → Sync Project with Gradle Files
```

Nếu không có lỗi đỏ → project đã sẵn sàng.

### Bước 6 — Chạy app

**Trên thiết bị thật (khuyến nghị):**
- Bật **Developer Options → USB Debugging** trên điện thoại
- Cắm USB → chọn thiết bị trong Android Studio → nhấn **Run ▶**

**Trên máy ảo (Emulator):**
- Tạo AVD: **Device Manager → Create Device** → chọn API 24+
- Nhấn **Run ▶**

**Không chạy được emulator → dùng Appetize** (xem phần trên)

---

## Cấu trúc project

```
TirTir-Mcommerce/
├── firestore.rules                          # Firestore Security Rules
└── app/src/main/
    ├── assets/
    │   └── viettelpost_stub.json            # Stub offline cho Viettel Post SOAP
    └── java/com/example/tirtir_mcommerce/
        ├── ui/
        │   ├── activities/                  # LoginActivity, RegisterActivity
        │   ├── fragments/                   # HomeFragment, ShopFragment, RoutineFragment, ProfileFragment
        │   └── adapters/                    # ProductAdapter, AddressAdapter
        ├── viewmodel/                       # AuthViewModel, ProfileViewModel
        ├── repository/                      # AuthRepository, ProfileRepository, ProductRepository
        ├── network/                         # RetrofitClient, ApiService, AuthInterceptor
        ├── model/                           # Product, User, Address, LoginRequest/Response, ...
        ├── database/                        # DatabaseHelper (Singleton, SQLite + FTS4)
        ├── utils/                           # SharedPrefsManager
        ├── WishlistContentProvider.java
        └── MainActivity.java
```

---

## Firestore Security Rules

File [`firestore.rules`](firestore.rules) đã được cấu hình với các quyền:

| Collection | Read | Write |
|---|---|---|
| `users/{userId}` | Chủ sở hữu | Chủ sở hữu |
| `products/{productId}` | Public | ❌ (chỉ backend) |
| `orders/{orderId}` | Chủ đơn hàng | Chủ đơn hàng |
| `wishlists/{userId}` | Chủ sở hữu | Chủ sở hữu |
| Tất cả còn lại | ❌ | ❌ |

---

## Checklist Foundation (S1)

### 1. Kiến trúc & Cấu hình

| | Item |
|---|---|
| ✅ | Cấu trúc package `ui`, `network`, `model`, `repository`, `viewmodel`, `database`, `utils` phân tách đúng MVVM |
| ✅ | `build.gradle` khai báo đủ Retrofit, Gson, Firebase BoM, Room, Glide, ksoap2 |
| ⚠️ | Firebase tích hợp — cần thêm `google-services.json` thủ công (không commit vì bảo mật) |

### 2. Network Layer

| | Item |
|---|---|
| ✅ | `RetrofitClient` Singleton, OkHttpClient timeout 60s + AuthInterceptor JWT |
| ✅ | `GET /api/v1/products` hoạt động — ShopFragment fetch + cache SQLite offline |
| ✅ | `ViettelPostSoapClient` — gọi SOAP thật qua ksoap2, tự fallback sang `assets/viettelpost_stub.json` khi offline/lỗi |

### 3. Database Layer

| | Item |
|---|---|
| ✅ | `DatabaseHelper.getInstance(context)` — Singleton pattern |
| ✅ | `CREATE VIRTUAL TABLE ingredient_conflicts USING fts4(...)` — full-text search thành phần |
| ✅ | `firestore.rules` — chặn truy cập trái phép, rules theo từng collection |

---


## Biến môi trường / Config

Không cần file `.env`. Tất cả config nằm trong code:

- **Backend URL:** [RetrofitClient.java](app/src/main/java/com/example/tirtir_mcommerce/network/RetrofitClient.java) → `BASE_URL = "https://tirtir-project.onrender.com/"`
- **Firebase:** tự động đọc từ `app/google-services.json`
- **Viettel Post stub:** [assets/viettelpost_stub.json](app/src/main/assets/viettelpost_stub.json)

---

## Liên hệ

Nếu thiếu file `google-services.json` hoặc cần thêm quyền Firebase, liên hệ project owner.
