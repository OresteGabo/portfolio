#!/bin/sh
set -eu
destination="${TARGET_BUILD_DIR:?}/${CONTENTS_FOLDER_PATH:?}/PlugIns/NextWidget.appex"
if [ "${GABO_WIDGETS_ENABLED:-NO}" != "YES" ]; then
    if [ -d "$destination" ]; then /bin/rm -rf "$destination"; fi
    exit 0
fi
source_bundle="${BUILT_PRODUCTS_DIR:?}/NextWidget.appex"
if [ ! -d "$source_bundle" ]; then
    echo 'Home widget extension is missing. Build the iosApp scheme with widgets enabled.' >&2
    exit 1
fi
/usr/bin/ditto "$source_bundle" "$destination"
