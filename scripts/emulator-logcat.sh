#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=android-env.sh
source "$ROOT_DIR/scripts/android-env.sh"

adb wait-for-device
adb logcat -c
echo "Streaming TirTir crash/runtime logs. Press Ctrl+C to stop."
adb logcat | grep -iE "FATAL EXCEPTION|AndroidRuntime|TirTir|CheckoutActivity|CartFragment|NullPointerException|IllegalStateException"

