---
description: Quy trình bật dự án TirTir mỗi lần làm việc
---

# Quy Trình Bật Dự Án TirTir

## Bước 1: Mở Docker Desktop
Tìm Docker Desktop trong Launchpad hoặc Applications và mở lên.
Đợi icon whale ở menu bar chuyển sang trạng thái chạy (không có vòng loading).

## Bước 2: Kéo code mới nhất (nếu làm việc nhóm)
```bash
git pull
```

## Bước 3: Bật backend + AI service + Redis qua Docker
// turbo
```bash
cd "/Users/pniamie/Phương Nghi/Năm 3 Kì 2/Web (NC)/TirtirProject_Group/TirTir-Project" && docker compose up -d
```

Lần đầu hoặc sau khi thay đổi code backend/AI dùng thêm `--build`:
```bash
docker compose up -d --build
```

Kiểm tra container đang chạy:
```bash
docker compose ps
```

## Bước 4: Bật Frontend (terminal riêng)
```bash
cd "/Users/pniamie/Phương Nghi/Năm 3 Kì 2/Web (NC)/TirtirProject_Group/TirTir-Project/frontend/tirtir-frontend" && ng serve
```

## Bước 5: Tắt Docker khi xong việc
```bash
cd "/Users/pniamie/Phương Nghi/Năm 3 Kì 2/Web (NC)/TirtirProject_Group/TirTir-Project" && docker compose down
```

---

## Ghi chú
- **Backend** (Node.js + nodemon): http://localhost:5001 — chạy trong Docker, hot-reload tự động khi sửa file
- **AI Service** (FastAPI Python 3.11): http://localhost:8000 — chạy trong Docker
- **Redis**: cổng 6379 — chạy trong Docker
- **Frontend** (Angular): http://localhost:4200 — chạy native NGOÀI Docker
- **Admin Frontend**: http://localhost:4201 (nếu có `ng serve --port 4201`)
- Sửa CSS đơn giản không gọi API → không cần bật Docker
- Sửa tính năng có gọi API → **bắt buộc bật Docker**
- Sentry đã được tích hợp, sẽ tự động báo lỗi khi user dùng
