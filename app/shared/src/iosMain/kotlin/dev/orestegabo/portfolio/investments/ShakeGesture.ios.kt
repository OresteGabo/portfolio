@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

@Composable
internal actual fun PlatformShakeListener(enabled: Boolean, onShake: () -> Unit) {
    val manager = remember { CMMotionManager() }
    val callback by rememberUpdatedState(onShake)
    DisposableEffect(manager, enabled) {
        val detector = ShakeDetector()
        var listening = enabled
        if (enabled && manager.accelerometerAvailable) {
            manager.accelerometerUpdateInterval = 0.02
            manager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
                if (data != null && listening) data.acceleration.useContents {
                    if (detector.sample((data.timestamp * 1000).toLong(), x, y, z)) callback()
                }
            }
        }
        onDispose { listening = false; manager.stopAccelerometerUpdates() }
    }
}
