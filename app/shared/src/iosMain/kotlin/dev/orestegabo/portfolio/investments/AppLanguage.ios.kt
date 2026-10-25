package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

@Composable
internal actual fun platformSystemLanguage(): String {
    fun current() = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    var language by remember { mutableStateOf(current()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { language = current() }
    return language
}
