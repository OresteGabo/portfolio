package dev.orestegabo.portfolio.investments

/** Explicit build-time override; never persisted over the user's production connection. */
expect fun developmentApiOrigin(): String?

internal fun developmentOrigin(enabled: Boolean, configured: String?): String? =
    configured?.trim()?.takeIf { enabled && it.isNotEmpty() && !it.startsWith("$(") }
