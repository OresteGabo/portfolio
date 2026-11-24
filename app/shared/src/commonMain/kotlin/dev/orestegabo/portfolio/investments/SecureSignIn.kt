package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable

/** Only opaque encrypted/native-keychain records persist; keys and auth contexts are dropped on lock. */
interface SecureSignIn {
    val available: Boolean
    val unlockLabel: String
    val authenticating: Boolean get() = false
    fun hasSaved(origin: String): Boolean
    suspend fun read(origin: String): String?
    /** Reuse this visit’s successful authentication; never open a new device prompt. */
    suspend fun readUnlocked(origin: String): String? = null
    suspend fun save(origin: String, value: String): Boolean
    fun delete(origin: String)
    fun lock()
}

@Composable expect fun rememberSecureSignIn(): SecureSignIn

@Composable expect fun NativePrivacyCover(protectCaptures: Boolean, protectDocument: Boolean = false)
