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

actual val captureProtectionRequiresMaskedNumbers get() = !shieldAvailable
actual val captureProtectionDescription get() = when {
    sceneCaptured && shieldAvailable -> "Only masked numbers appear in screen captures"
    !shieldAvailable -> "Screen shield unavailable; numbers stay hidden"
    else -> "Captures show masked numbers; your screen stays readable"
}
