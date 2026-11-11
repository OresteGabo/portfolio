package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*

/** Navigation requests only. Authentication and every import/save remain in the normal app flow. */
object NativeAppActions {
    private val allowed = setOf("import-pdf", "add-purchase", "calendar")
    var pending by mutableStateOf<String?>(null)
        private set
    fun request(action: String): Boolean {
        if (action !in allowed) return false
        pending = action
        return true
    }
    fun take(): String? = pending.also { pending = null }
    fun clear() { pending = null }
}
