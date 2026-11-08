@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.get
import platform.CoreFoundation.*
import platform.CoreGraphics.*
import kotlin.test.*

class CaptureBackdropLayerTest {
    @Test fun aRedactedSnapshotCannotChangeTheVisibleLayerOrRevealItsPixels() = runTest {
        val context = SkiaGraphicsContext()
        val actual = context.createGraphicsLayer()
        val masked = context.createGraphicsLayer()
        val parent = context.createGraphicsLayer()
        try {
            actual.record(Density(1f), LayoutDirection.Ltr, IntSize(8, 8)) { drawRect(Color.Red) }
            masked.record(Density(1f), LayoutDirection.Ltr, IntSize(8, 8)) { drawRect(Color.Blue) }
            masked.alpha = 0f
            parent.record(Density(1f), LayoutDirection.Ltr, IntSize(8, 8)) { drawLayer(actual); drawLayer(masked) }
            assertEquals(Color.Red, parent.toImageBitmap().toPixelMap()[4, 4])
            val registry = CaptureBackdropRegistry().apply { add(actual, masked) }
            val capture = registry.redacted { assertNotNull(immediateCaptureBitmap(parent)) }
            assertEquals(1f, actual.alpha)
            assertEquals(0f, masked.alpha)
            assertEquals(Color.Blue, capture.toPixelMap()[4, 4])
            assertEquals(Color.Red, parent.toImageBitmap().toPixelMap()[4, 4])
            // Restoring the live renderer cannot mutate a previously produced capture bitmap.
            assertEquals(Color.Blue, capture.toPixelMap()[4, 4])
            // The worker receives only immutable masked pixels, after the live layers are restored.
            val preview = withContext(Dispatchers.Default) { assertNotNull(capture.toBackdropImage()) }
            val cgImage = assertNotNull(preview.CGImage)
            val data = assertNotNull(CGDataProviderCopyData(assertNotNull(CGImageGetDataProvider(cgImage))))
            try {
                val bytes = assertNotNull(CFDataGetBytePtr(data))
                val offset = 4 * CGImageGetBytesPerRow(cgImage).toInt() + 4 * 4
                assertEquals(0u.toUByte(), bytes[offset])
                assertEquals(0u.toUByte(), bytes[offset + 1])
                assertEquals(255u.toUByte(), bytes[offset + 2])
                assertEquals(255u.toUByte(), bytes[offset + 3])
            } finally { CFRelease(data) }
        } finally {
            context.releaseGraphicsLayer(parent)
            context.releaseGraphicsLayer(masked)
            context.releaseGraphicsLayer(actual)
            context.dispose()
        }
    }
}
