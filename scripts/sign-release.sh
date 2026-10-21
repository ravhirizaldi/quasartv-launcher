#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

quasar_signing_dir="${QUASARTV_SIGNING_DIR:-$HOME/.config/quasartv}"
quasar_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
quasar_apksigner="$quasar_sdk_root/build-tools/35.0.0/apksigner"
quasar_release_dir="app/build/outputs/apk/release"

"$quasar_apksigner" sign --lineage "$quasar_signing_dir/signing-lineage.bin" \
    --rotation-min-sdk-version 28 --debuggable-apk-permitted false --v4-signing-enabled false \
    --ks "$quasar_signing_dir/legacy-debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android \
    --next-signer --ks "$quasar_signing_dir/release.p12" --ks-key-alias quasartv-release \
    --ks-pass "file:$quasar_signing_dir/release-password.txt" \
    --out "$quasar_release_dir/app-release.apk" "$quasar_release_dir/app-release-unsigned.apk"
"$quasar_apksigner" verify --verbose --print-certs "$quasar_release_dir/app-release.apk"
