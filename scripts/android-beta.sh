#!/bin/sh
# Build only. Never creates keys or uploads artifacts.
set -eu
cd "$(dirname "$0")/.."
: "${STRUCT_UPLOAD_STORE_FILE:?Set the upload keystore path}"
: "${STRUCT_UPLOAD_STORE_PASSWORD:?Set the keystore password}"
: "${STRUCT_UPLOAD_KEY_ALIAS:?Set the upload key alias}"
: "${STRUCT_UPLOAD_KEY_PASSWORD:?Set the upload key password}"
./gradlew --no-configuration-cache -PrequireReleaseSigning=true \
  :app:androidApp:lintRelease :app:androidApp:bundleRelease
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('app/androidApp/build/outputs/bundle/release/androidApp-release.aab') as bundle:
    assert any(name.startswith('META-INF/') and name.endswith(('.RSA', '.EC', '.DSA'))
               for name in bundle.namelist()), 'Bundle has no signing certificate'
PY
jarsigner -verify app/androidApp/build/outputs/bundle/release/androidApp-release.aab
