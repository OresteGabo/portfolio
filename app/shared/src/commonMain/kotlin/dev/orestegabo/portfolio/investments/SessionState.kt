package dev.orestegabo.portfolio.investments

/** Local operating mode is separate from device lock and verified server identity. */
sealed interface SessionState {
    data object SignedOut : SessionState
    data class Guest(val path: GuestPath) : SessionState
    data class Authenticated(val accountId: String, val origin: String) : SessionState
}
enum class GuestPath(val stored: String) {
    PERSONAL("guest-personal-v1"), SAMPLE("guest-sample-v1");
    companion object { fun restore(value: String?) = entries.firstOrNull { it.stored == value } }
}
internal const val MANUAL_ACCOUNT_ENTRY = "account-manual-v1"

internal fun restoredSessionChoice(raw: String?): String? = when {
    raw == null -> null
    GuestPath.restore(raw) != null || raw == MANUAL_ACCOUNT_ENTRY -> raw
    else -> MANUAL_ACCOUNT_ENTRY // Unknown metadata must not restore an unrelated saved account.
}
