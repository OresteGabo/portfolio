package dev.orestegabo.portfolio.investments

import androidx.compose.ui.text.input.PlatformImeOptions

enum class CredentialInput { USERNAME, PASSWORD, NEW_PASSWORD }

/** Semantics drive Android AutoFill; iOS additionally needs explicit UIKit input traits. */
expect fun credentialImeOptions(input: CredentialInput): PlatformImeOptions?

// Never share credentials from test/custom servers with the production website's password entry.
internal fun passwordSharingDomain(origin: String): String? =
    if (origin == DEFAULT_ORIGIN) "bonds.orestegabo.dev" else null
