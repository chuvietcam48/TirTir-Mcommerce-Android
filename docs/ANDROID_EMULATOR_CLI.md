# TirTir Android emulator CLI

This project can run on the local Android emulator without Appetize.

## Available local SDK

The scripts expect the Android SDK at:

```bash
~/Library/Android/sdk
```

The current local AVD is:

```bash
medium_phone
```

## One-command local run

Use this when the backend is running on the Mac at `http://localhost:5001/`:

```bash
./scripts/emulator-run-local.sh
```

The Android emulator reaches the host Mac through `10.0.2.2`, so the script
builds the APK with:

```bash
TIRTIR_API_BASE_URL=http://10.0.2.2:5001/
```

## Step-by-step commands

Start emulator:

```bash
./scripts/emulator-start.sh
```

Build, install, and launch the app:

```bash
./scripts/emulator-install.sh
```

Watch useful crash/runtime logs:

```bash
./scripts/emulator-logcat.sh
```

Load Android CLI tools into the current terminal:

```bash
source ./scripts/android-env.sh
```

Then `adb`, `emulator`, `sdkmanager`, and `avdmanager` are available in that
terminal session.

## Appetize build

For Appetize, use the default deployed HTTPS backend:

```bash
./gradlew assembleDebug
```

