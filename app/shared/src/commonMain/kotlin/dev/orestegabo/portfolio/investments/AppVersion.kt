package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable

internal data class AppVersion(val name: String, val build: String?)

/** Installed app metadata, independent of legal-document versions and server releases. */
@Composable
internal expect fun rememberAppVersion(): AppVersion?
