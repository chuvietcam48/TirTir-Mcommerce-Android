#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=android-env.sh
source "$ROOT_DIR/scripts/android-env.sh"

cat <<EOF
Android CLI is ready for this shell.

ANDROID_HOME=$ANDROID_HOME
ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT

Common commands:
  adb devices
  emulator -list-avds
  sdkmanager --list_installed
  avdmanager list avd
  ./scripts/emulator-start.sh
  ./scripts/emulator-install.sh
  ./scripts/emulator-logcat.sh
  ./scripts/emulator-run-local.sh

To load adb/emulator/sdkmanager into your current terminal:
  source ./scripts/android-env.sh
EOF

