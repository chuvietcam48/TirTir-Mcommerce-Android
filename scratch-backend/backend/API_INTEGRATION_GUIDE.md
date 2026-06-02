# Frontend to Backend Integration Guide - TirTir Admin Dashboard

Chào bạn Frontend Developer! 👋
Đây là tài liệu hướng dẫn chi tiết để bạn kết nối giao diện Admin với hệ thống Backend vừa mới được nâng cấp.

## 1. Thông tin chung (General Info)
*   **Base URL:** `http://localhost:5001/api/v1`
*   **Authentication:** Tất cả các API Admin đều yêu cầu đăng nhập.
*   **Header Required:**
    ```json
    {
      "Authorization": "Bearer <YOUR_TOKEN_HERE>",
      "Content-Type": "application/json"
    }
    ```
*   **Admin Account (Mặc định):**
    *   Email: `admin@tirtir.com`
    *   Password: `admin123`

---

## 2. Admin Authentication Flow
Trước khi gọi bất kỳ API nào bên dưới, bạn cần thực hiện login để lấy Token.

### Login API
*   **Endpoint:** `POST /auth/login`
*   **Payload:**
    ```json
    {
      "email": "admin@tirtir.com",
      "password": "admin123"
    }
    ```
*   **Response:** Lưu lại `token` và `user` info vào `localStorage` hoặc State Management (NgRx/Redux).
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIs...",
      "user": { "id": "...", "role": "admin", ... }
    }
    ```

---

## 3. Dashboard Overview (Thống kê)
Dùng cho trang chủ của Admin Dashboard.

### Lấy số liệu tổng quan (Stats)
*   **Endpoint:** `GET /admin/dashboard/stats`
*   **Response:**
    ```json
    {
      "totalRevenue": 15000000,
      "ordersByStatus": { "Pending": 5, "Delivered": 12, "Cancelled": 2 },
      "newCustomersCount": 45,
      "topSellingProducts": [ ... ],
      "salesByCategory": [ ... ]
    }
    ```

### Biểu đồ doanh thu (Revenue Chart)
*   **Endpoint:** `GET /admin/dashboard/revenue`
*   **Query Params (Optional):** `?startDate=2024-01-01&endDate=2024-02-01`
*   **Response:** Array dữ liệu để vẽ biểu đồ Line/Bar.
    ```json
    [
      { "_id": "2024-02-10", "revenue": 500000, "count": 2 },
      { "_id": "2024-02-11", "revenue": 1200000, "count": 5 }
    ]
    ```

### Danh sách đơn hàng (Order Management)
*   **Endpoint:** `GET /admin/orders`
*   **Query Params:** `?page=1&limit=10&status=Pending`
*   **Response:**
    ```json
    {
      "orders": [ ... ],
      "page": 1,
      "pages": 5,
      "total": 50
    }
    ```

---

## 4. Product Management (Quản lý sản phẩm)
CRUD đầy đủ cho trang danh sách sản phẩm.

### Tạo sản phẩm mới
*   **Endpoint:** `POST /admin/products`
*   **Payload:**
    ```json
    {
      "Product_ID": "NEW-001",
      "Name": "New Cushion Foundation",
      "Price": 450000,
      "Category": "Makeup",
      "Stock_Quantity": 100,
      "Thumbnail_Images": "https://url...",
      "Description_Short": "Short description"
    }
    ```

### Cập nhật sản phẩm
*   **Endpoint:** `PUT /admin/products/:id` (id có thể là `_id` hoặc `Product_ID`)
*   **Payload:** Gửi các trường cần sửa.
    ```json
    {
      "Price": 420000,
      "Stock_Quantity": 120
    }
    ```

### Xóa sản phẩm
*   **Endpoint:** `DELETE /admin/products/:id`

### Nhập kho nhanh (Quick Stock Update)
*   **Endpoint:** `PATCH /admin/products/:id/stock`
*   **Payload:**
    ```json
    {
      "stock": 200,
      "reason": "Restock from Vendor"
    }
    ```

### Bulk Import (Nhập hàng loạt)
*   **Endpoint:** `POST /admin/products/bulk-import`
*   **Payload:** Array các sản phẩm (như payload tạo mới).

---

## 5. Inventory & Alerts (Kho hàng)

### Cảnh báo tồn kho (Low Stock)
*   **Endpoint:** `GET /inventory/alerts`
*   **Query Params:** `?threshold=10` (Mặc định 10)
*   **Response:**
    ```json
    {
      "lowStock": { "count": 5, "items": [ ... ] },
      "deadStock": { "count": 2, "items": [ ... ] }
    }
    ```

### Lịch sử nhập xuất (Audit Log)
*   **Endpoint:** `GET /inventory/logs`
*   **Query Params:** `?limit=20&productId=...`
*   **Response:**
    ```json
    [
      {
        "action": "Sale",
        "change_type": "Decrease",
        "balance_before": 100,
        "balance_after": 99,
        "reason": "Order Confirmed",
        "createdAt": "2024-02-11T..."
      }
    ]
    ```

---

## 6. Angular Implementation Tips

### Service Example (`admin.service.ts`)
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private apiUrl = 'http://localhost:5001/api/v1/admin';

  constructor(private http: HttpClient) {}

  getDashboardStats(): Observable<any> {
    return this.http.get(`${this.apiUrl}/dashboard/stats`);
  }

  getOrders(page: number, status?: string): Observable<any> {
    let params = `?page=${page}`;
    if (status) params += `&status=${status}`;
    return this.http.get(`${this.apiUrl}/orders${params}`);
  }

  createProduct(product: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/products`, product);
  }
}
```

### Auth Interceptor
Đừng quên cấu hình Interceptor để tự động gắn Token vào mọi request.

```typescript
// auth.interceptor.ts
intercept(req: HttpRequest<any>, next: HttpHandler) {
  const token = localStorage.getItem('token');
  if (token) {
    const cloned = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${token}`)
    });
    return next.handle(cloned);
  }
  return next.handle(req);
}
```

Chúc bạn kết nối thành công! Nếu gặp lỗi 401/403, hãy kiểm tra lại Token và Role của user nhé. 🚀
