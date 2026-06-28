#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=android-env.sh
source "$ROOT_DIR/scripts/android-env.sh"

"$ROOT_DIR/scripts/emulator-start.sh" "${1:-${ANDROID_AVD_NAME:-medium_phone}}"
TIRTIR_API_BASE_URL="${TIRTIR_API_BASE_URL:-http://10.0.2.2:5001/}" \
  "$ROOT_DIR/scripts/emulator-install.sh"

