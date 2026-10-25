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
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.UIKit.*
import kotlin.coroutines.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val BackdropNotification = "portfolio.captureBackdrop"

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

private fun ImageBitmap.toBackdropImage(): UIImage? {
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
    var frameRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(enabled) {
        if (enabled) while (isActive) {
            delay(50)
            if (registry.revision != registry.capturedRevision) frameRevision++
        }
    }
    CompositionLocalProvider(LocalCaptureBackdropRegistry provides if (enabled) registry else null) {
        Box(Modifier.fillMaxSize().drawWithContent {
            frameRevision // Invalidate the parent recording when cached child layers update.
            scene.record { this@drawWithContent.drawContent() }
            if (enabled) {
                val preview = registry.redacted { immediateCaptureBitmap(scene)?.toBackdropImage() }
                if (preview != null) NSNotificationCenter.defaultCenter.postNotificationName(BackdropNotification,
                    `object` = null, userInfo = mapOf("image" to preview))
            }
            registry.captured()
            drawLayer(scene)
        }) { content() }
    }
}
