#!/bin/sh
# Xcode and Android Studio share one framework location. Always let Gradle check
# source changes, even when the IDE marks an existing framework as already built.
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"
unset OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED
./gradlew :app:shared:embedAndSignAppleFrameworkForXcode
