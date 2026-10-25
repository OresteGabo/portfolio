@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UISelectionFeedbackGenerator

@Composable
internal actual fun rememberReducedMotion(): Boolean {
    var enabled by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null, queue = NSOperationQueue.mainQueue) { enabled = UIAccessibilityIsReduceMotionEnabled() }
        enabled = UIAccessibilityIsReduceMotionEnabled()
        onDispose { center.removeObserver(observer) }
    }
    return enabled
}

@Composable
internal actual fun rememberSelectionFeedback(): () -> Unit {
    val generator = remember { UISelectionFeedbackGenerator() }
    return remember(generator) { { generator.selectionChanged(); generator.prepare() } }
}
