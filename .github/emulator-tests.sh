#!/usr/bin/env bash
set -euo pipefail
collect() {
  mkdir -p screenshots
  adb pull /sdcard/Download/monthmark-screenshots/. screenshots/ || true
  adb logcat -d -b crash -v threadtime > screenshots/crashes.txt || true
  adb shell dumpsys appwidget > screenshots/widget-state.txt || true
}
trap collect EXIT
./gradlew :app:connectedDebugAndroidTest --no-daemon
