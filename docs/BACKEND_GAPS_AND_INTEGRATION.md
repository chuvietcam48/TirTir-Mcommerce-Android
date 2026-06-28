# TirTir Mobile Backend Integration Gaps

Verified on 2026-06-28 against the configured deployed API and the latest pulled `main`.

## Working contracts

- Product catalog and product media are live (`/api/v1/products` and `/assets/...`).
- Product shades are live (`/api/v1/shades`).
- Notifications and latest skin profile routes exist and correctly require authentication.
- The Android client keeps the API base URL in `BuildConfig`; database URIs and credentials are not embedded in the app.

## Deployment blockers

1. The deployed API returns 404 for `POST /api/v1/payments/arbitrate`. Checkout can collect and validate the complete order, but cannot create its authoritative order/payment result until this route is deployed.
2. The deployed API returns 404 for the shipping location routes used by checkout (`/api/shipping/locations/provinces`, districts, and wards). The repository contains these routes under `scratch-backend/shipping`, but the deployed service does not expose them.
3. The deployed API returns 404 for routine save/community (`POST /api/v1/routines/save`, `GET /api/v1/routines/community`). Recommendation/profile endpoints exist, but sharing and applying community routines cannot complete live.
4. The repository has two backend applications (`scratch-backend/` and `scratch-backend/backend/`) with different route sets and response envelopes. They need one deployment contract. Mobile product-detail parsing currently accepts both the direct product and `{ data: product }` shapes as a compatibility bridge.
5. Storefront product/order values are USD, while some shipping/admin code treats values as VND. The backend must publish one currency contract and convert carrier fees before adding them to an order total.
6. Firebase Admin credentials are not available in the local backend environment. Push delivery, Firestore routine sharing, and realtime order synchronization therefore remain disabled locally. Credentials must be supplied through environment/secret configuration, never committed or hardcoded.
7. Routine sharing depends on each MongoDB account being mapped to a Firebase UID. The backend should backfill or create this mapping during authentication so save/share does not fail for older users.
8. Notification records currently expose broad order/promotion/system types. Dedicated routine and skin-report types plus a stable deep-link payload are still needed for fully authoritative notification grouping.

## Required backend deployment order

1. Choose and deploy one backend entrypoint with products, auth, cart, orders, payments, shipping, notifications, AI, routines, loyalty, and admin routes.
2. Configure `MONGO_URI`, Firebase Admin, carrier, and payment credentials only through the deployment secret manager.
3. Normalize response envelopes and currency, then publish the API contract.
4. Run authenticated smoke tests for cart sync → shipping quote → order arbitration → payment/deep-link → order detail, and skin profile → routine recommendation → save/share/apply.
