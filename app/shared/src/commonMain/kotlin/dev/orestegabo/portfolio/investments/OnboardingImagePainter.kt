package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

internal data class OnboardingImageBounds(
    val canvasWidth: Int, val canvasHeight: Int,
    val left: Int, val top: Int, val width: Int, val height: Int,
)

/** Decode only the occupied part of a layer; retain its original viewport for Fit and animation pivots. */
@Composable
internal fun onboardingPainter(resource: DrawableResource): Painter {
    val source = painterResource(resource)
    val bounds = onboardingMotionBounds(resource) ?: onboardingDocumentBounds(resource)
    return remember(source, bounds) { if (bounds == null) source else OnboardingImagePainter(source, bounds) }
}

internal class OnboardingImagePainter(
    private val source: Painter,
    private val bounds: OnboardingImageBounds,
) : Painter() {
    private var opacity = 1f
    private var filter: ColorFilter? = null
    override val intrinsicSize = Size(bounds.canvasWidth.toFloat(), bounds.canvasHeight.toFloat())

    override fun applyAlpha(alpha: Float): Boolean { opacity = alpha; return true }
    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean { filter = colorFilter; return true }

    override fun DrawScope.onDraw() {
        // BitmapPainter rounds the full destination to integer pixels. Round that same
        // viewport once, then transform source pixels; rounding each cropped layer's
        // destination independently would shift thin lines by a fraction of a pixel.
        val sx = size.width.roundToInt().toFloat() / bounds.canvasWidth
        val sy = size.height.roundToInt().toFloat() / bounds.canvasHeight
        scale(sx, sy, pivot = Offset.Zero) {
            translate(bounds.left.toFloat(), bounds.top.toFloat()) {
                with(source) { draw(Size(bounds.width.toFloat(), bounds.height.toFloat()), alpha = opacity, colorFilter = filter) }
            }
        }
    }
}
