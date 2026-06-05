# ARCHITECTURE.md — TirTir Foundation Finder

This document explains the project structure and what each folder/module is responsible for.

---

## 1) High-level Architecture

**Frontend (Angular)** → calls **Backend REST API (Node.js/Express)** → reads/writes **MongoDB**  
Backend also runs **Shade Matching (kNN + color utilities)** as an internal service.

**Main user journeys**
1) E-commerce: browse → product detail → add to cart → checkout → orders
2) Virtual shade finder: consent → webcam / upload → ROI (auto landmarks + manual adjust) → match → explain → add-to-cart
3) Skin routine chatbox: user Q&A → asks about skin type/concerns → recommends routine + products

---

## 2) Repository Layout

/backend
/frontend/tirtir-frontend (Angular app)

markdown
Sao chép mã

Backend currently contains `.env`, `.env.example`, `package.json`, `server.js`, and core folders like `config/`, `controllers/`, `middlewares/`, `models/`, `routes/`, `services/`, `validators/` :contentReference[oaicite:3]{index=3} :contentReference[oaicite:4]{index=4}

Frontend root is an Angular project (`angular.json`, `tsconfig*.json`, `.vscode/`, etc.) :contentReference[oaicite:5]{index=5}

> Note: your `frontend_tree.txt` is dominated by `node_modules`, so it may not show `src/` clearly. That’s normal when you export tree including node_modules.

---

## 3) Backend Structure (Node.js + Express + MongoDB)

### 3.1 Entry points & config

- **`server.js`**
  - App bootstrap: load env, connect MongoDB, mount routes, start server. :contentReference[oaicite:6]{index=6}
- **`.env` / `.env.example`**
  - Store `MONGO_URI`, `PORT`, JWT secrets, etc. :contentReference[oaicite:7]{index=7}
- **`/config/`**
  - Mongo connection setup (recommended: `config/db.js`).

### 3.2 MVC-ish separation (routes → controllers → models)

- **`/routes/`**
  - Defines endpoints and connects them to controllers.
  - Currently has `shade.routes.js` :contentReference[oaicite:8]{index=8}
  - Planned: `product.routes.js`, `cart.routes.js`, `order.routes.js`, `analysis.routes.js`, `feedback.routes.js`, `chat.routes.js`

- **`/controllers/`**
  - Handles request/response logic (HTTP layer).
  - Example responsibilities:
    - validate input (or call validators)
    - call service methods
    - return JSON response with status codes :contentReference[oaicite:9]{index=9}

- **`/models/`**
  - Mongoose schemas (data layer).
  - Currently includes `shade.model.js` :contentReference[oaicite:10]{index=10}
  - Planned models:
    - `product.model.js` (cosmetic products)
    - `order.model.js`
    - `skinAnalysis.model.js` (stores capture + ROI + result + explanation)
    - `feedback.model.js` (feedback CRUD)
    - `chatSession.model.js` (optional, if you store chat history)

### 3.3 Services (business logic)

- **`/services/`**
  - Business logic that is NOT just HTTP handlers.
  - You already have `services/` and `services/ml/` placeholders :contentReference[oaicite:11]{index=11}

- **`/services/ml/`**
  - Everything about shade matching:
    - color conversion utils (RGB/HEX → LAB)
    - distance calculation
    - kNN matching
    - explanation builder (why this shade) :contentReference[oaicite:12]{index=12}

### 3.4 Middlewares & validators

- **`/middlewares/`**
  - Cross-cutting concerns:
    - auth (JWT)
    - role check (admin)
    - upload (multer)
    - global error handler :contentReference[oaicite:13]{index=13}

- **`/validators/`**
  - Request validation per route (body/query params)
  - You already have `validators/` placeholder :contentReference[oaicite:14]{index=14}

### 3.5 CRUD mapping (what features require CRUD)

**Required CRUDs (per syllabus)**
- Shade CRUD (admin): create/read/update/delete shades (dataset)
- Product CRUD (admin): create/read/update/delete products
- SkinAnalysis CRUD (user): create/read/list/delete analyses (privacy)
- Feedback CRUD (user): create/read/update/delete feedback
- Order CRUD (partial): create order + read history

CRUD is **NOT** a file/framework — it’s a **pattern of endpoints + controllers + models**.

---

## 4) Frontend Structure (Angular)

Frontend is a standard Angular app root (`angular.json`, `tsconfig...`, `.vscode/`, etc.) :contentReference[oaicite:15]{index=15}  
Recommended internal architecture inside `src/app/` (clean + dễ chia team):

### 4.1 `src/app/core/` (singleton, app-wide)
- Guards (auth/admin)
- Interceptors (JWT attach token)
- API services (call backend)

### 4.2 `src/app/shared/` (reusable UI + types)
- Shared components (header/footer/buttons)
- Pipes (currency, format)
- Interfaces/models (Product, Shade, Order, Feedback…)

### 4.3 `src/app/features/` (feature modules / pages)
- `auth/` (login/register)
- `shop/` (home, product list, product detail)
- `cart/`, `orders/`
- `shade-finder/` (scan/upload + ROI + result + explainable panel)
- `chatbox/` (skin routine assistant UI)
- `admin/` (product/shade management)

---

## 5) API Contract (how FE knows “API lấy gì từ đâu”)

**Rule:** FE never touches DB directly. FE only calls API endpoints.

Typical API groups:
- `/api/health` — server check
- `/api/shades` — shade dataset CRUD
- `/api/products` — products CRUD + listing
- `/api/skin-analyses` — upload/capture metadata + history
- `/api/match` — run shade matching + return explanation
- `/api/feedbacks` — feedback CRUD (linked to analysis/order)
- `/api/cart` + `/api/orders` — checkout flow
- `/api/chat` — routine assistant (returns routine + recommended products)

---

## 6) Dataset Strategy (keep it small but demo-able)

### 6.1 Shades dataset (TirTir brand only — recommended for January deadline)
A small-but-good dataset: **25–40 shades** is enough for demo.

Each shade record:
- `shadeCode` (e.g. "21N")
- `shadeName`
- `hex` (swatch color)
- `undertone` (cool/neutral/warm)
- `depth` (light/medium/deep)
- `productId` (links to product)
- optional: `imageUrl` (reuse product images if needed)

### 6.2 Product dataset (no SKU, no inventory)
Minimum: **10–20 products** (foundation + skincare basics)
Fields:
- `productId`
- `name`, `category`, `price`, `salePrice`
- `mainConcern`, `skinTypeTarget`, `keyIngredients`
- `imageUrls[]`
- `description`, `howToUse`

---

## 7) Conventions

- Keep `node_modules/` out of Git (frontend + backend)
- Keep `uploads/` (if any) out of Git
- Use `.env.example` for required env vars (never commit `.env`) :contentReference[oaicite:16]{index=16}

---

## 8) How to print the project tree (to verify structure)

**Windows CMD**
- `tree /A /F`

**PowerShell (exclude heavy folders)**
- `Get-ChildItem -Recurse -Force | Where-Object { $_.FullName -notmatch 'node_modules|dist|.angular' } | Select-