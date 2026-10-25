@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.*
import kotlinx.coroutines.test.runTest
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
        } finally {
            context.releaseGraphicsLayer(parent)
            context.releaseGraphicsLayer(masked)
            context.releaseGraphicsLayer(actual)
            context.dispose()
        }
    }
}
