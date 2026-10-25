package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Three separate acceleration peaks within 700 ms distinguish a shake from a single bump. */
internal class ShakeDetector {
    private var lastSample = Long.MIN_VALUE
    private var firstPeak = 0L
    private var lastPeak = 0L
    private var peaks = 0
    private var aboveThreshold = false
    private var cooldownUntil = Long.MIN_VALUE

    // Both platforms supply acceleration including gravity, measured in g.
    fun sample(timeMs: Long, x: Double, y: Double, z: Double): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return false
        if (timeMs < lastSample) reset()
        lastSample = timeMs
        val strengthSquared = x * x + y * y + z * z
        if (timeMs < cooldownUntil) return false
        if (strengthSquared < 1.4 * 1.4) aboveThreshold = false
        if (strengthSquared < 2.2 * 2.2 || aboveThreshold) return false
        aboveThreshold = true
        if (peaks == 0 || timeMs - firstPeak > 700) {
            firstPeak = timeMs
            peaks = 1
        } else if (timeMs - lastPeak >= 70) {
            peaks++
        }
        lastPeak = timeMs
        if (peaks < 3) return false
        peaks = 0
        cooldownUntil = timeMs + 1800
        return true
    }

    private fun reset() {
        peaks = 0
        aboveThreshold = false
        cooldownUntil = Long.MIN_VALUE
    }
}

@Composable
fun ShakeNumberVisibility(enabled: Boolean, onShake: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    PlatformShakeListener(enabled && resumed, onShake)
}

@Composable internal expect fun PlatformShakeListener(enabled: Boolean, onShake: () -> Unit)
