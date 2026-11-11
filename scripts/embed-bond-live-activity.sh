#!/bin/sh
set -eu

# Normal device builds do not need a second Apple provisioning profile.
# Only push-enabled builds embed the signed Live Activity extension.
destination="${TARGET_BUILD_DIR:?}/${CONTENTS_FOLDER_PATH:?}/PlugIns/BondLiveActivity.appex"
if [ "${GABO_PUSH_ENABLED:-NO}" != "YES" ]; then
    # Clear the generated extension when reusing a previously push-enabled build directory.
    if [ -d "$destination" ]; then /bin/rm -rf "$destination"; fi
    exit 0
fi
source_bundle="${BUILT_PRODUCTS_DIR:?}/BondLiveActivity.appex"
if [ ! -d "$source_bundle" ]; then
    echo 'Live Activity extension is missing. Build the iosApp scheme with push enabled.' >&2
    exit 1
fi
/usr/bin/ditto "$source_bundle" "$destination"
