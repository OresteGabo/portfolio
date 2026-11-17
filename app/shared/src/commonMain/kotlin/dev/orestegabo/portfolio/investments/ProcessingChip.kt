package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import portfolio.app.shared.generated.resources.*
import kotlin.math.PI
import kotlin.math.sin

/** The whole package face is an etched Inzira circuit, projected onto the silicon plane. */
@Composable
internal fun ProcessingChip(dark: Boolean, progress: Float, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val energy = onboardingPhase(progress, .19f, .29f) * (1f - onboardingPhase(progress, .52f, .63f))
    val charge = energy * (.80f + .20f * sin(progress * 90f))
    val etching = remember {
        // Same isometric projection as the source package: x=320+120(u-v), y=100+66(u+v).
        // The mark spans u/v .08..92, rather than sitting inside a separate central die.
        val plane = Matrix(floatArrayOf(
            120f * .84f / 440f, 66f * .84f / 440f, 0f, 0f,
            -120f * .84f / 400f, 66f * .84f / 400f, 0f, 0f,
            0f, 0f, 1f, 0f,
            320f, 110.56f, 0f, 1f,
        ))
        fun projected(data: String) = PathParser().parsePathString(data).toPath().apply { transform(plane) }
        listOf(
            projected(InziraBrandGeometry.INITIAL),
            projected(InziraBrandGeometry.FRAME),
            projected(InziraBrandGeometry.JOURNEY),
        )
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * .42f)
            val radius = size.width * .40f
            drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = .22f * charge), ink.copy(alpha = 0f)),
                center = center, radius = radius), radius, center)
        }
        Image(onboardingPainter(if (dark) Res.drawable.onboarding_processor_blank_dark else Res.drawable.onboarding_processor_blank_light),
            null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            // Match ContentScale.Fit exactly, so the paths are flush with the bitmap's metal face.
            val unit = minOf(size.width / 320f, size.height / 240f)
            translate((size.width - 320f * unit) / 2f, (size.height - 240f * unit) / 2f) {
                scale(unit, unit, pivot = Offset.Zero) {
                    translate(-160f, -70f) {
                        val edge = Stroke(1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        for (fold in etching.take(2)) {
                            // Opposed rims make the line read as a recess in the metal, not a sticker.
                            translate(0f, .75f) { drawPath(fold, ink, alpha = .16f, style = Stroke(2.2f)) }
                            drawPath(fold, ink, alpha = .72f, style = edge)
                            drawPath(fold, ink, alpha = charge * .14f, style = Stroke(5f))
                        }
                        val channel = etching.last()
                        drawPath(channel, ink, alpha = .18f, style = Stroke(.8f, cap = StrokeCap.Round))
                        for (start in listOf(.22f, .34f, .46f)) {
                            val packet = ((progress - start) / .12f).coerceIn(0f, 1f)
                            if (packet > 0f && packet < 1f) {
                                val measure = PathMeasure().apply { setPath(channel, false) }
                                val distance = measure.length * packet
                                val trail = Path()
                                measure.getSegment((distance - measure.length * .25f).coerceAtLeast(0f), distance, trail, true)
                                val fade = minOf(packet / .12f, (1f - packet) / .12f, 1f)
                                drawPath(trail, ink, alpha = .18f * fade, style = Stroke(7f, cap = StrokeCap.Round))
                                drawPath(trail, ink, alpha = fade, style = Stroke(2f, cap = StrokeCap.Round))
                                drawCircle(ink, 1.7f, measure.getPosition(distance), alpha = fade)
                            }
                        }
                        val release = onboardingPhase(progress, .50f, .64f)
                        val fade = sin(PI * release).toFloat().coerceAtLeast(0f)
                        val rim = Path().apply {
                            val expansion = release * 12f
                            moveTo(320f, 100f - expansion)
                            lineTo(440f + expansion, 166f)
                            lineTo(320f, 232f + expansion)
                            lineTo(200f - expansion, 166f)
                            close()
                        }
                        drawPath(rim, ink, alpha = .35f * fade, style = Stroke(1f))
                    }
                }
            }
        }
    }
}
