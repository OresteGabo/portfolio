@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.UIKit.*
import kotlin.coroutines.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val BackdropNotification = "portfolio.captureBackdrop"

/** A capture is useful only after the live scene settles, and only for that exact scene. */
internal class IosCaptureRefresh {
    var recordedRevision = 0L
        private set
    private var frame = 0L
    private var publishedFrame = 0L
    private var lastDrawMillis = 0L

    fun drawn(revision: Long, nowMillis: Long) {
        recordedRevision = revision
        changed(nowMillis)
    }

    fun changed(nowMillis: Long) {
        frame++
        lastDrawMillis = nowMillis
    }

    fun pendingFrame(nowMillis: Long): Long? = frame.takeIf {
        it != publishedFrame && nowMillis - lastDrawMillis >= 160L
    }

    fun isCurrent(candidate: Long) = candidate == frame
    fun published(candidate: Long) { publishedFrame = candidate }
}

private fun uptimeMillis() = (NSProcessInfo.processInfo.systemUptime * 1_000).toLong()

/** The pinned Skia implementation is synchronous; reject future asynchronous snapshots safely. */
internal fun immediateCaptureBitmap(layer: GraphicsLayer): ImageBitmap? {
    var accepting = true
    var bitmap: ImageBitmap? = null
    suspend { layer.toImageBitmap() }.startCoroutine(object : Continuation<ImageBitmap> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<ImageBitmap>) { if (accepting) bitmap = result.getOrNull() }
    })
    accepting = false
    return bitmap
}

internal fun ImageBitmap.toBackdropImage(): UIImage? {
    val pixels = IntArray(width * height)
    readPixels(pixels)
    val rgba = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { index, argb ->
        val offset = index * 4
        rgba[offset] = (argb shr 16).toByte()
        rgba[offset + 1] = (argb shr 8).toByte()
        rgba[offset + 2] = argb.toByte()
        rgba[offset + 3] = (argb ushr 24).toByte()
    }
    val data = rgba.toData()
    val retained = CFBridgingRetain(data)
    val provider = try { CGDataProviderCreateWithCFData(retained?.reinterpret()) } finally { CFRelease(retained) } ?: return null
    val colors = CGColorSpaceCreateDeviceRGB()
    try {
        val image = CGImageCreate(width.toULong(), height.toULong(), 8u, 32u, (width * 4).toULong(), colors,
            CGImageAlphaInfo.kCGImageAlphaLast.value, provider, null, false, CGColorRenderingIntent.kCGRenderingIntentDefault) ?: return null
        return try { UIImage.imageWithCGImage(image) } finally { CGImageRelease(image) }
    } finally { CGColorSpaceRelease(colors); CGDataProviderRelease(provider) }
}

@Composable
internal actual fun CaptureBackdropHost(enabled: Boolean, content: @Composable () -> Unit) {
    val registry = remember { CaptureBackdropRegistry() }
    val scene = rememberGraphicsLayer()
    val refresh = remember { IosCaptureRefresh() }
    val scrollObserver = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Lazy lists can scroll cached child layers without rerunning the parent's draw.
                // Observe both drags and fling steps without consuming any of their movement.
                if (consumed != Offset.Zero || available != Offset.Zero) refresh.changed(uptimeMillis())
                return Offset.Zero
            }
        }
    }
    var frameRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(enabled) {
        if (enabled) while (isActive) {
            delay(50)
            // Cached child layers can change without redrawing the parent. Request one recording
            // per revision, rather than repeatedly invalidating while waiting for an idle capture.
            if (registry.revision != refresh.recordedRevision) {
                frameRevision++
                continue
            }
            val frame = refresh.pendingFrame(uptimeMillis()) ?: continue
            // Snapshot and restore the layer alphas together on the UI thread. Never pass the
            // mutable live layer to a worker; only this immutable, already-redacted image leaves it.
            val bitmap = registry.redacted { immediateCaptureBitmap(scene) } ?: continue
            val revision = registry.revision
            val preview = withContext(Dispatchers.Default) { bitmap.toBackdropImage() } ?: continue
            // Navigation, scrolling or privacy changes during conversion supersede this result.
            if (!refresh.isCurrent(frame) || registry.revision != revision) continue
            NSNotificationCenter.defaultCenter.postNotificationName(BackdropNotification,
                `object` = null, userInfo = mapOf("image" to preview))
            refresh.published(frame)
            registry.captured()
        }
    }
    CompositionLocalProvider(LocalCaptureBackdropRegistry provides if (enabled) registry else null) {
        Box(Modifier.fillMaxSize().nestedScroll(scrollObserver).drawWithContent {
            frameRevision // Invalidate the parent recording when cached child layers update.
            scene.record { this@drawWithContent.drawContent() }
            refresh.drawn(registry.revision, uptimeMillis())
            drawLayer(scene)
        }) { content() }
    }
}
