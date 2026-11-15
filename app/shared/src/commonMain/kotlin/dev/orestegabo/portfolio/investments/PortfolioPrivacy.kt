package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*

/** Local privacy choices have no account identity or API dependency. */
internal class PortfolioPrivacy(private val settings: ServerSettings, private val captureMaskRequired: Boolean?) {
    var screenCaptureAllowed by mutableStateOf(settings.readScreenCaptureAllowed())
        private set
    val captureProtectionEnabled get() = !screenCaptureAllowed
    val numbersRevealBlocked get() = captureProtectionEnabled && (captureMaskRequired ?: captureProtectionRequiresMaskedNumbers)
    val numbersConcealed get() = numbersHidden || numbersRevealBlocked
    fun updateScreenCapturePermission(allowed: Boolean) {
        settings.writeScreenCaptureAllowed(allowed)
        screenCaptureAllowed = allowed
    }
    var shakeToHideEnabled by mutableStateOf(settings.readShakeToHideEnabled())
        private set
    fun updateShakeToHide(enabled: Boolean) {
        settings.writeShakeToHideEnabled(enabled)
        shakeToHideEnabled = enabled
    }
    var numbersHidden by mutableStateOf(settings.readNumbersHidden())
        private set
    fun toggleNumberVisibility(): Boolean {
        if (numbersRevealBlocked) {
            return false
        }
        settings.writeNumbersHidden(!numbersHidden)
        numbersHidden = !numbersHidden
        return true
    }
}
