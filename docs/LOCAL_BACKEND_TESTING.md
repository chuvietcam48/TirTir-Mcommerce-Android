# TirTir Android local backend testing

## Backend and MongoDB

Run from `scratch-backend/backend`:

```bash
npm install
node scripts/seed-shade-color.js
node scripts/seed-lipstick-shades.js
npm start
```

The backend is expected at `http://localhost:5001/`.

The two shade scripts only update matching products already stored in MongoDB.
They do not create the base product catalog.

## Android emulator

Android emulators reach the host machine through `10.0.2.2`, not `localhost`.

```bash
./gradlew assembleDebug -PTIRTIR_API_BASE_URL=http://10.0.2.2:5001/
```

Debug builds allow cleartext HTTP for local development. Release builds do not.

## Appetize

Appetize cannot access a backend running on a developer laptop. Build with the
default deployed HTTPS endpoint:

```bash
./gradlew assembleDebug
```

An Appetize session that reinstalls the APK or clears app data also clears the
locally saved access and refresh tokens. The account remains in MongoDB, so the
same credentials should sign in again without registering a new account.
