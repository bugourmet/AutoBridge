#!/usr/bin/env bash
# Enforces the privacy guarantee: fails if the APK requests INTERNET or can't be inspected.
set -euo pipefail

apk="$1"
aapt2=$(ls "$ANDROID_HOME"/build-tools/*/aapt2* | sort -V | tail -1)
permissions=$("$aapt2" dump permissions "$apk")

if ! grep -q '^package: ' <<<"$permissions"; then
  echo "::error::Couldn't read permissions from $apk"
  exit 1
fi
if grep -q 'android.permission.INTERNET' <<<"$permissions"; then
  echo "::error::$apk requests INTERNET"
  exit 1
fi
echo "$apk doesn't request INTERNET"
