package dev.orestegabo.portfolio.investments

actual fun developmentApiOrigin(): String? =
    developmentOrigin(PlatformBuild.debug, PlatformBuild.apiOrigin)
