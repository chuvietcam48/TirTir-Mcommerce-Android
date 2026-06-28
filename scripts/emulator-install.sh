#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=android-env.sh
source "$ROOT_DIR/scripts/android-env.sh"

API_BASE_URL="${TIRTIR_API_BASE_URL:-http://10.0.2.2:5001/}"
PACKAGE_NAME="com.example.tirtir_mcommerce"
MAIN_ACTIVITY="$PACKAGE_NAME/.ui.activities.SplashActivity"

cd "$ROOT_DIR"
echo "Building TirTir debug APK with API base URL: $API_BASE_URL"
./gradlew assembleDebug -PTIRTIR_API_BASE_URL="$API_BASE_URL"

adb wait-for-device
adb install -r -t -d app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n "$MAIN_ACTIVITY"
echo "Installed and launched $PACKAGE_NAME"
