package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.*

internal const val IMPORT_FLOW_DESCRIPTION = "A broker PDF is extracted and reviewed. Bond records, share records and purchase costs feed your portfolio mix, estimated value and coupon dates. Review the details before saving."

/** Native labels and connected paths explain the data flow; the supplied art supplies its components. */
@Composable
internal fun ImportFlowDiagram(dark: Boolean, active: Boolean = false) {
    val progress = rememberOnboardingMotion(active, 5_800)
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp).clearAndSetSemantics {
        contentDescription = localized(IMPORT_FLOW_DESCRIPTION)
    }, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        // Keep the same connected illustration on every phone. Only its captions scale to fit;
        // the explanation below retains the user's text size and the diagram has a spoken summary.
        val captionScale = (maxWidth.value / 340f).coerceIn(.8f, 1f)
        CompositionLocalProvider(LocalDensity provides Density(density.density, captionScale)) {
            WideImportFlow(dark, progress)
        }
    }
}

@Composable
private fun WideImportFlow(dark: Boolean, progress: Float) {
    val ink = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()
    val packets = remember(ink, textMeasurer) { listOf("01", "10", "11").map {
        textMeasurer.measure(it, TextStyle(color = ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
    } }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(440f / 290f)) {
        val scale = maxWidth / 440.dp
        fun at(x: Float, y: Float, w: Float, h: Float) = Modifier.offset(x.dp * scale, y.dp * scale)
            .size(w.dp * scale, h.dp * scale)
        // Every route terminates at an input, result, or labelled field. There are no decorative loose traces.
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 440f
            val sy = size.height / 290f
            fun route(x: Float, y: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float,
                      endX: Float, endY: Float, incoming: Boolean = false, start: Float, end: Float) {
                val strands = if (incoming) listOf(0f) else listOf(-4f, 0f, 4f)
                strands.forEach { offset ->
                    val path = Path().apply {
                        moveTo(x * sx, (y + offset) * sy)
                        cubicTo(cx1 * sx, (cy1 + offset * 1.8f) * sy,
                            cx2 * sx, (cy2 + offset * 1.5f) * sy, endX * sx, (endY + offset) * sy)
                    }
                    drawPath(path, ink.copy(alpha = if (offset == 0f) .42f else .22f),
                        style = Stroke((if (incoming) 1.1.dp else .7.dp).toPx(), cap = StrokeCap.Round))
                    if (offset == 0f) {
                        val travel = (progress - start) / (end - start)
                        drawOnboardingSignal(path, travel.coerceIn(0f, 1f), ink)
                        val measure = PathMeasure().apply { setPath(path, false) }
                        packets.forEachIndexed { index, glyphs ->
                            val flight = travel * 1.4f - index * .20f
                            if (flight > 0f && flight < 1f) {
                                val position = measure.getPosition(measure.length * flight)
                                val alpha = minOf(flight / .1f, (1f - flight) / .12f, 1f)
                                drawText(glyphs, topLeft = position + Offset(-glyphs.size.width / 2f, -12.dp.toPx()), alpha = alpha)
                            }
                        }
                    }
                    if (!incoming) drawCircle(ink.copy(alpha = .85f), 1.2.dp.toPx(), Offset(endX * sx, (endY + offset) * sy))
                }
                if (incoming) {
                    val head = Path().apply {
                        moveTo((endX - 5) * sx, (endY - 4) * sy); lineTo(endX * sx, endY * sy)
                        lineTo((endX - 5) * sx, (endY + 4) * sy)
                    }
                    drawPath(head, ink, style = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            route(88f, 52f, 125f, 52f, 126f, 144f, 157f, 144f, incoming = true, start = .03f, end = .29f)
            // Traces begin beneath the opaque processor face, emerge between its pins, and end on item ports.
            route(242f, 129f, 313f, 129f, 300f, 46f, 340f, 46f, start = .65f, end = .79f)
            route(251f, 149f, 310f, 149f, 312f, 142f, 340f, 142f, start = .69f, end = .83f)
            route(242f, 164f, 305f, 164f, 300f, 238f, 340f, 238f, start = .73f, end = .87f)
            route(196f, 154f, 140f, 154f, 148f, 197f, 120f, 197f, start = .34f, end = .49f)
            route(202f, 168f, 145f, 168f, 156f, 232f, 120f, 232f, start = .38f, end = .53f)
            route(211f, 178f, 151f, 178f, 163f, 267f, 120f, 267f, start = .42f, end = .57f)
        }
        Image(painterResource(documentArt(dark)), null, at(12f, 4f, 94f, 84f), contentScale = ContentScale.Fit)
        Canvas(at(12f, 4f, 94f, 84f)) {
            val scan = onboardingPhase(progress, .01f, .25f)
            val intensity = sin(PI * scan).toFloat().coerceAtLeast(0f)
            val y = size.height * (.24f + .54f * scan)
            drawRect(Brush.verticalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = .16f * intensity)),
                startY = y - 8.dp.toPx(), endY = y),
                Offset(size.width * .30f, y - 8.dp.toPx()), Size(size.width * .46f, 8.dp.toPx()))
            drawLine(ink, Offset(size.width * .30f, y), Offset(size.width * .76f, y),
                1.dp.toPx(), alpha = intensity * .9f)
        }
        FlowLabel("Broker PDF", at(2f, 96f, 116f, 32f))
        FlowLabel("Extract & review", at(142f, 48f, 160f, 28f), strong = true)
        ProcessingChip(dark, progress, at(138f, 90f, 168f, 116f))
        val review = sin(PI * onboardingPhase(progress, .48f, .64f)).toFloat()
        Surface(at(161f, 210f, 149f, 70f), color = ink.copy(alpha = .07f * review),
            shape = RoundedCornerShape(10.dp)) {
            FlowLabel("Review before saving", Modifier.fillMaxSize(), strong = true, muted = review < .1f)
        }
        listOf("Bond records", "Share records", "Costs & fees").forEachIndexed { i, label ->
            Surface(at(4f, 183f + 35f * i, 116f, 28f), shape = RoundedCornerShape(8.dp),
                color = ink.copy(alpha = .045f), border = BorderStroke(1.dp, ink.copy(alpha = .16f + .32f *
                    sin(PI * onboardingPhase(progress, .43f + i * .04f, .59f + i * .04f)).toFloat()))) {
                FlowLabel(label, Modifier.padding(horizontal = 4.dp))
            }
        }
        OutputNode(OnboardingChart.ALLOCATION, "Portfolio mix", onboardingPhase(progress, .77f, .97f), at(340f, 4f, 96f, 92f))
        OutputNode(OnboardingChart.VALUE, "Estimated value", onboardingPhase(progress, .81f, .99f), at(340f, 100f, 96f, 92f))
        OutputNode(OnboardingChart.DATES, "Coupon dates", onboardingPhase(progress, .85f, 1f), at(340f, 196f, 96f, 92f))
    }
}

@Composable
private fun OutputNode(kind: OnboardingChart, label: String, progress: Float, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = ink.copy(alpha = .025f),
        border = BorderStroke(.7.dp, ink.copy(alpha = .15f))) {
        Column(Modifier.fillMaxSize().padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OnboardingChartArtwork(kind, progress, Modifier.fillMaxWidth().heightIn(max = 52.dp).weight(1f))
            FlowLabel(label, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FlowLabel(label: String, modifier: Modifier = Modifier, strong: Boolean = false, muted: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AppText(label, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center)
    }
}

private fun documentArt(dark: Boolean) = if (dark) Res.drawable.onboarding_reports_dark else Res.drawable.onboarding_reports_light
