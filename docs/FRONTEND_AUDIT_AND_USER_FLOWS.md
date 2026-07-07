# TirTir Android Frontend Audit and User Flows

Updated: 2026-06-14

## Scope

- Code changes are limited to the Android frontend.
- Backend code in `scratch-backend` was read only to verify API contracts.
- Default app language is English.
- API-driven data must use loading, empty, error, and offline states. Demo orders,
  simulated admin saves, and developer-facing "coming in phase" messages are not
  valid production states.

## Current End-to-End User Flow

### 1. First Launch and Authentication

1. Splash checks onboarding and the local authenticated session.
2. First-time users see the TirTir onboarding journey.
3. Users can sign in, create an account, or request a password reset.
4. Successful registration reuses the returned session or performs one automatic
   sign-in attempt, avoiding the previous sign-up-twice test loop.
5. Role routing sends customers to the commerce app and admins to Admin Dashboard.
6. Google Sign-In stays hidden until a valid Android OAuth client is configured.

### 2. Product Discovery

1. Home loads products and dynamic filters from the product API.
2. While loading, the UI displays a loading state; cached products are used offline.
3. Search filters the loaded catalog.
4. Category uses a bounded dialog selector while the chip row filters skin type only;
   both filters can be combined with search.
5. Quick actions open AI Advisor, Skin Analysis, and Ingredient Scan.
6. Product cards support product details, wishlist toggle, and quick add to cart.
7. Missing remote images show a branded TirTir placeholder instead of an empty block.

### 3. Product Detail and Assisted Conversion

1. Product Detail loads the selected product and refreshes deep-linked products by ID.
2. Users review gallery, price, stock, skin suitability, ingredients, and description.
3. Quantity is bounded by stock.
4. Add to Cart writes locally first and attempts server synchronization.
5. Buy Now adds the selected quantity and opens Checkout.
6. Wishlist is available regardless of stock.
7. AI Advisor opens with product context.
8. Ingredient Scan receives the known product ingredient list when available.
9. AR Try-On opens the full-screen shade interface; real face tracking remains blocked
   until an AR SDK/model is integrated.

### 4. Wishlist

1. Users add or remove products from Home or Product Detail.
2. Wishlist remains available offline through the Android ContentProvider.
3. Wishlist shows item count, remove action, product image fallback, and empty state.
4. Server wishlist synchronization still needs a dedicated repository integration and
   Mongo `_id` mapping; the backend endpoint exists.

### 5. Cart

1. Add-to-cart is local-first for responsive offline behavior.
2. Quantity changes, swipe-to-delete, and remove actions update local storage and call
   the cart API.
3. Cart badge reflects local quantity.
4. Empty cart provides a Continue Shopping path.
5. Shipping is not fabricated in Cart; it is deferred to Checkout.

### 6. Checkout and Orders

1. Checkout validates recipient and shipping fields.
2. Supported payment values are `CARD`, `VNPAY`, and `MOMO`, matching backend validation.
3. Checkout re-reads the local cart, blocks empty/invalid submissions, prefills the
   cached default address, and prevents duplicate order taps.
4. Pending local-first cart items must finish syncing to the server before the real
   create-order request is sent.
5. Missing token, address, payment, API body, or order ID produces a recoverable
   user-facing state rather than closing the screen.
6. Success clears the local cart and displays the real order ID.
7. View Order History opens the API-backed order list directly.
8. Order History supports status filters and only shows invoice download when the API
   provides a real `invoiceUrl`.

### 7. AI Advisor

1. Signed-in users load recent chat history.
2. Suggested prompts provide a useful first action instead of a blank screen.
3. Messages stream from the chat SSE endpoint.
4. Product recommendation chips deep-link to Product Detail.
5. Offline and retry states are user-facing and contain no developer roadmap text.

### 8. Skin Analysis

1. The app explains and requests camera permission, supports Open Settings after
   denial, and tries the rear camera if the front camera is unavailable.
2. The user aligns their face in the oval guide.
3. Capture sends the real JPEG as base64 to `/api/v1/ai/analyze-face`.
4. Result displays skin tone, undertone, and calculated ITA angle when LAB debug values
   are returned.
5. Cushion products come from the product API rather than a hardcoded list.
6. When camera/ML is unavailable, users can retry or open a clearly labelled demo
   result without making the production response look real.
7. Texture, pores, hydration, exact skin hex, and ranked shade-match scores cannot be
   truthful until backend includes these values.

### 9. Ingredient Scan

1. The app explains and requests camera permission, supports Open Settings after
   denial, and tries an alternate camera when necessary.
2. CameraX captures a real image while keeping retry on the same screen.
3. Product-origin scans display ingredients already returned by Product API.
4. If OCR is unavailable, a clearly labelled demo scan keeps emulator/Appetize
   presentations usable and demonstrates the conflict-result flow.
5. Conflict Result supports ingredient chips and severity cards.
6. Scan History loads the authenticated user's real history endpoint.
7. Production camera OCR and conflict inference remain blocked because no analysis
   endpoint exists.

### 10. Routine Builder

1. Users switch between AM, PM, and Community.
2. Empty slots use compact add-product affordances rather than oversized blank cards.
3. Product selection uses the real product catalog.
4. Only selected steps can be dragged.
5. SPF appears only in AM.
6. Save and Share stays disabled until at least one product is selected.
7. The suggestion card is only visible while its target step is missing, and View Picks
   opens Home with the relevant product search.
8. Selected routine data is stored locally and shared through the Android share sheet.
9. Community routines and cloud persistence remain blocked by missing APIs.

### 11. Account

1. Profile loads cached data immediately and refreshes from API.
2. Edit Profile updates the real profile endpoint.
3. Avatar selection supports camera/gallery and Firebase upload.
4. Skin type has a clear single selected state.
5. Loyalty summary loads points, tier, and progress from `/api/v1/loyalty/me`.
6. My Orders, My Addresses, Wishlist, Scan History, and Notification Settings are linked.
7. Address add, edit, delete, and set-default actions use the real API.
8. Content includes bottom safe-area padding and consistent English labels.

### 12. Notifications

1. Five notification types are available.
2. Switches persist bidirectionally in SharedPreferences.
3. Routine Reminder opens a time picker.
4. FCM token registration exists, but payload deep links need backend agreement and
   end-to-end testing.

## Current Admin Flow

### 1. Dashboard

1. Admin role opens Admin Dashboard after authentication.
2. Overview cards, revenue trend, top products, and order status charts load real admin
   API data.
3. No sample chart values are injected on API failure.

### 2. Product Management

1. Product list loads the real product catalog.
2. Search filters by product name, category, or SKU.
3. Add/Edit uses multipart create/update on `/api/v1/admin/products`.
4. The first selected image is sent as the thumbnail.
5. Deactivate and active-state toggle call real admin endpoints.
6. Backend currently requires `Product_ID` although its create controller appears to
   generate one without assigning it back to `req.body`; Android temporarily sends a
   generated `APP-{timestamp}` SKU so creation can pass validation.

### 3. Order Management

1. Admin order list loads from API.
2. Status changes use backend-valid states: Pending, Processing, Shipped, Delivered,
   and Cancelled.
3. Status updates are persisted through the admin endpoint.

### 4. Cart Recovery and Churn

1. Cart Recovery displays live aggregate totals.
2. Individual abandoned carts cannot be shown because the backend exposes no list
   endpoint.
3. Churn tabs contain a professional unavailable state rather than fake users.
4. Targeted voucher and FCM actions cannot be enabled safely without a real churn/RFM
   user list containing stable user IDs.

## Backend Requests

| Priority | Backend request | Status |
| --- | --- | --- |
| P0 | Serve or replace all product `Thumbnail_Images`, gallery, and description asset URLs | Pending |
| P0 | Implement refresh-token rotation endpoint and interceptor contract | Pending |
| P0 | Return authoritative checkout totals, shipping fee, tax, discounts, loyalty redemption, and payment status | **Completed** (Handled via `/api/v1/payments/arbitrate`) |
| P0 | Confirm payment initiation/deep-link flow for CARD, VNPAY, and MOMO; add COD only if supported | **Completed** (VNPAY deep links integrated) |
| P0 | Add Ingredient OCR/conflict-analysis endpoint accepting an image and returning ingredients plus severity conflicts | **Dropped** (Feature cancelled by user) |
| P0 | Extend skin analysis response with `skinHex`, ITA category, texture, pores, hydration, and ranked cushion/shade matches | **Completed** (Frontend CameraX ML now handles texture/pores live) |
| P1 | Add community routine list/apply/like and user routine save/update/delete/share APIs | **Completed** (Integrated via `/api/routines/...`) |
| P1 | Add individual abandoned-cart list endpoint with user, cart items, value, last activity, and recovery state | **Completed** (Integrated via `/api/v1/admin/churn/abandoned-carts`) |
| P1 | Add RFM/churn user list endpoint with segment, stable user ID, contact permission, and last activity | **Completed** |
| P1 | Add invoice PDF endpoint or return `invoiceUrl` on order responses | **Completed** |
| P1 | Configure Android OAuth client in Firebase and define backend Google token exchange | Pending |
| P1 | Normalize server wishlist IDs or accept both Mongo `_id` and `Product_ID` | Pending |
| P1 | Add order detail timeline, cancel, and reorder response contracts to API documentation | Pending |
| P1 | Return checkout loyalty multiplier explicitly when applicable | Pending |
| P2 | Add notification preference synchronization and define FCM payload deep-link keys | Pending |
| P2 | Provide AR SDK/model contract and shade asset metadata | Pending |
| P2 | Add Loyalty Wallet, barcode scan, and voucher wallet APIs/screens for SCR-25, SCR-26, and SCR-27 | Pending |

## Runtime Verification Note

- The requested checkout logcat command was attempted on June 14, 2026.
- `adb devices` returned no connected device, and `adb logcat` waited indefinitely for
  a device.
- A Pixel/API 34 AVD could not be installed because the machine had only about 3.2 GB
  free and the Android SDK installer failed with `No space left on device`.
- No FATAL stacktrace has been fabricated. Checkout was hardened from verified code
  paths and must still be reproduced once Appetize logs or a connected emulator are
  available.

## Frontend Follow-Up

- Complete server wishlist merge/sync after backend accepts a stable product identifier.
- Build Order Detail with tracking, cancel, and reorder once response schemas are frozen.
- Add coupon and loyalty redemption controls only after server-authoritative totals exist.
- Add review list/create flow using the existing review routes.
- Add real AR capture/share after the AR renderer exists.
- Run visual regression on Pixel 6 API 34 and a compact device after each Appetize upload.
