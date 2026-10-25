package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// The native host reports readiness before financial content can be revealed.
private var shieldAvailable by mutableStateOf(false)
private var sceneCaptured by mutableStateOf(false)

/** Called on the main thread by the native UIKit host, including cold start and transitions. */
fun updateIosCaptureShield(available: Boolean, captured: Boolean) {
    shieldAvailable = available
    sceneCaptured = captured
}

actual val captureProtectionRequiresMaskedNumbers get() = !shieldAvailable || sceneCaptured
actual val captureProtectionDescription get() = when {
    sceneCaptured -> "Numbers hidden while screen sharing is active"
    !shieldAvailable -> "Screen shield unavailable; numbers stay hidden"
    else -> "Best-effort capture shielding; numbers stay visible"
}
