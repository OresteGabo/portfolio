#!/bin/sh
set -eu
destination="${TARGET_BUILD_DIR:?}/${CONTENTS_FOLDER_PATH:?}/PlugIns/ShareImport.appex"
if [ "${GABO_WIDGETS_ENABLED:-NO}" != "YES" ]; then
    if [ -d "$destination" ]; then /bin/rm -rf "$destination"; fi
    exit 0
fi
source_bundle="${BUILT_PRODUCTS_DIR:?}/ShareImport.appex"
if [ ! -d "$source_bundle" ]; then echo 'PDF Share extension is missing from this build.' >&2; exit 1; fi
/usr/bin/ditto "$source_bundle" "$destination"
