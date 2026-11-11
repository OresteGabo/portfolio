package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.*
import kotlin.math.PI
import kotlin.math.sin

/** The mark rotates in the die's projected plane, independently of the package and pins. */
@Composable
internal fun ProcessingChip(dark: Boolean, progress: Float, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val energy = onboardingPhase(progress, .19f, .29f) * (1f - onboardingPhase(progress, .52f, .63f))
    val charge = energy * (.80f + .20f * sin(progress * 90f))
    val rotation = onboardingPhase(progress, .24f, .56f)
    BoxWithConstraints(modifier) {
        val unit = maxWidth / 168.dp
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 168f
            val sy = size.height / 116f
            val center = Offset(84f * sx, 48f * sy)
            drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = .30f * charge), ink.copy(alpha = 0f)),
                center = center, radius = 66f * sx), 66f * sx, center)
        }
        Image(painterResource(if (dark) Res.drawable.onboarding_processor_blank_dark else Res.drawable.onboarding_processor_blank_light),
            null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 168f
            val sy = size.height / 116f
            fun die(expansion: Float) = Path().apply {
                moveTo(84f * sx, (31f - expansion * .55f) * sy)
                lineTo((112f + expansion) * sx, 46.4f * sy)
                lineTo(84f * sx, (62f + expansion * .55f) * sy)
                lineTo((56f - expansion) * sx, 46.4f * sy); close()
            }
            for ((width, alpha) in listOf(7f to .06f, 4f to .13f, 1.2f to .70f)) {
                drawPath(die(1f), ink, alpha = alpha * charge, style = Stroke(width.dp.toPx()))
            }
            drawOnboardingSignal(die(6f), ((progress - .22f) / .12f).coerceIn(0f, 1f), ink)
            drawOnboardingSignal(die(6f), ((progress - .34f) / .12f).coerceIn(0f, 1f), ink)
            drawOnboardingSignal(die(6f), ((progress - .46f) / .12f).coerceIn(0f, 1f), ink)
            // A finite power ripple leaves the die as processing completes.
            val release = onboardingPhase(progress, .50f, .64f)
            val fade = sin(PI * release).toFloat().coerceAtLeast(0f)
            drawPath(die(24f * release), ink, alpha = .42f * fade, style = Stroke(1.dp.toPx()))
        }
        StructMark(Modifier.offset(64.dp * unit, 26.4.dp * unit).size(40.dp * unit)
            .graphicsLayer { scaleY = .55f }
            .graphicsLayer { rotationZ = 45f + 720f * rotation }, onDark = dark)
    }
}
