package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.PlatformBuild

actual fun developmentApiOrigin(): String? =
    developmentOrigin(PlatformBuild.debug, PlatformBuild.apiOrigin)
