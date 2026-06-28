#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=android-env.sh
source "$ROOT_DIR/scripts/android-env.sh"

AVD_NAME="${1:-${ANDROID_AVD_NAME:-medium_phone}}"
EMULATOR_LOG="${TMPDIR:-/tmp}/tirtir-${AVD_NAME}-emulator.log"
EMULATOR_MEMORY_MB="${ANDROID_EMULATOR_MEMORY_MB:-1536}"

if ! avdmanager list avd | grep -q "Name: $AVD_NAME"; then
  echo "AVD '$AVD_NAME' was not found." >&2
  echo "Available AVDs:" >&2
  avdmanager list avd >&2
  exit 1
fi

if adb devices | grep -qE '^emulator-[0-9]+[[:space:]]+device$'; then
  echo "An Android emulator is already running."
  adb devices
  exit 0
fi

echo "Starting Android emulator: $AVD_NAME"
echo "Emulator log: $EMULATOR_LOG"
nohup emulator \
  -avd "$AVD_NAME" \
  -memory "$EMULATOR_MEMORY_MB" \
  -no-snapshot-load \
  -no-snapshot-save \
  -no-boot-anim \
  -no-audio \
  -netdelay none \
  -netspeed full \
  >"$EMULATOR_LOG" 2>&1 &

adb wait-for-device
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
  printf "."
  sleep 2
done
printf "\nEmulator is ready.\n"
adb devices
