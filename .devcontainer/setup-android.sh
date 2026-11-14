#!/usr/bin/env bash
set -euo pipefail
sdk_root="${ANDROID_HOME:-/home/vscode/android-sdk}"
if [ ! -x "$sdk_root/cmdline-tools/latest/bin/sdkmanager" ]; then
  temp_dir="$(mktemp -d)"
  trap 'rm -rf "$temp_dir"' EXIT
  curl --fail --location --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$temp_dir/tools.zip"
  unzip -q "$temp_dir/tools.zip" -d "$temp_dir/unpacked"
  mkdir -p "$sdk_root/cmdline-tools"
  mv "$temp_dir/unpacked/cmdline-tools" "$sdk_root/cmdline-tools/latest"
fi
set +o pipefail
yes | "$sdk_root/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk_root" --licenses > /dev/null
set -o pipefail
"$sdk_root/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk_root" 'platform-tools' 'platforms;android-35' 'build-tools;34.0.0'
./gradlew :core:test :app:assembleDebug --no-daemon
