#!/bin/sh
# Xcode and Android Studio share one framework location. A clean may remove it
# even when the IDE sets its "already built Kotlin" override for a build-only action.
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"
framework="$project_root/app/shared/build/xcode-frameworks/${CONFIGURATION:?}/${SDK_NAME:?}/Shared.framework/Shared"

if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" = "YES" ] && [ -s "$framework" ]; then
    ./gradlew :app:shared:syncComposeResourcesForIos
else
    # Let the Gradle integration regenerate and expose a missing framework.
    unset OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED
    ./gradlew :app:shared:embedAndSignAppleFrameworkForXcode
fi
