package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.*
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun CalendarReminderArtwork(dark: Boolean, active: Boolean) {
    val progress = rememberOnboardingMotion(active, 4_800)
    val ink = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * (760f / 560f))
        Box(Modifier.size(width, width * (560f / 760f))) {
            Image(painterResource(if (dark) Res.drawable.onboarding_calendar_base_dark else Res.drawable.onboarding_calendar_base_light),
                null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Image(painterResource(if (dark) Res.drawable.onboarding_calendar_phone_dark else Res.drawable.onboarding_calendar_phone_light),
                null, Modifier.fillMaxSize().graphicsLayer {
                    val first = onboardingPhase(progress, .35f, .57f)
                    val second = onboardingPhase(progress, .71f, .93f)
                    rotationZ = -2.2f * sin(2f * PI.toFloat() * first) - 1.6f * sin(2f * PI.toFloat() * second)
                }, contentScale = ContentScale.Fit)
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / 760f
                val sy = size.height / 560f
                val selected = Path().apply {
                    moveTo(181f * sx, 196f * sy); lineTo(293f * sx, 193f * sy)
                    lineTo(300f * sx, 257f * sy); lineTo(188f * sx, 260f * sy); close()
                }
                val selection = onboardingPhase(progress, .01f, .16f) * (1f - onboardingPhase(progress, .26f, .40f))
                drawPath(selected, ink, alpha = .20f * selection)
                drawPath(selected, ink, alpha = .65f * selection, style = Stroke(1.2.dp.toPx()))
                fun delivery(x: Float, y: Float, from: Float, until: Float) {
                    val p = onboardingPhase(progress, from, until)
                    if (p > 0f && p < 1f) for (index in 0..1) {
                        val wave = (p * 1.4f - index * .3f).coerceIn(0f, 1f)
                        drawCircle(ink, (18f + 55f * wave) * sx, Offset(x * sx, y * sy),
                            alpha = (1f - wave) * .35f, style = Stroke(1.dp.toPx()))
                    }
                }
                delivery(548f, 302f, .40f, .65f)
                delivery(581f, 391f, .74f, .98f)
                fun reminder(start: Offset, c1: Offset, c2: Offset, end: Offset, from: Float, until: Float) {
                    val p = ((progress - from) / (until - from)).coerceIn(0f, 1f)
                    val emphasis = sin(PI * onboardingPhase(progress, from - .05f, until)).toFloat()
                    val center = Offset(start.x * sx, start.y * sy)
                    drawCircle(ink, (22f + 13f * emphasis) * sx, center, alpha = .08f * emphasis)
                    drawCircle(ink, (27f + 16f * emphasis) * sx, center, alpha = .32f * emphasis,
                        style = Stroke(1.dp.toPx()))
                    val path = Path().apply {
                        moveTo(center.x, center.y)
                        cubicTo(c1.x * sx, c1.y * sy, c2.x * sx, c2.y * sy, end.x * sx, end.y * sy)
                    }
                    drawPath(path, ink, alpha = .20f * emphasis, style = Stroke(.8.dp.toPx()))
                    drawOnboardingSignal(path, p, ink)
                }
                reminder(Offset(366f, 222f), Offset(450f, 180f), Offset(477f, 300f), Offset(548f, 302f), .12f, .40f)
                reminder(Offset(404f, 361f), Offset(474f, 330f), Offset(504f, 391f), Offset(581f, 391f), .48f, .74f)
            }
            Image(painterResource(if (dark) Res.drawable.onboarding_calendar_notice_upper_dark else Res.drawable.onboarding_calendar_notice_upper_light),
                null, Modifier.fillMaxSize().graphicsLayer {
                    val arrival = onboardingPhase(progress, .36f, .55f)
                    alpha = arrival
                    translationX = -15.dp.toPx() * (1f - arrival)
                    translationY = 12.dp.toPx() * (1f - arrival)
                    scaleX = .86f + .14f * arrival + .08f * sin(PI * arrival).toFloat()
                    scaleY = scaleX
                }, contentScale = ContentScale.Fit)
            Image(painterResource(if (dark) Res.drawable.onboarding_calendar_notice_lower_dark else Res.drawable.onboarding_calendar_notice_lower_light),
                null, Modifier.fillMaxSize().graphicsLayer {
                    val arrival = onboardingPhase(progress, .70f, .89f)
                    alpha = arrival
                    translationX = -15.dp.toPx() * (1f - arrival)
                    translationY = 12.dp.toPx() * (1f - arrival)
                    scaleX = .86f + .14f * arrival + .08f * sin(PI * arrival).toFloat()
                    scaleY = scaleX
                }, contentScale = ContentScale.Fit)
        }
    }
}

@Composable
internal fun ReportAssemblyArtwork(dark: Boolean, active: Boolean) {
    val progress = rememberOnboardingMotion(active, 4_300)
    val ink = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * (320f / 380f))
        Box(Modifier.size(width, width * (380f / 320f))) {
            val sheets = if (dark) listOf(Res.drawable.onboarding_report_sheet_0_dark,
                Res.drawable.onboarding_report_sheet_1_dark, Res.drawable.onboarding_report_sheet_2_dark)
            else listOf(Res.drawable.onboarding_report_sheet_0_light,
                Res.drawable.onboarding_report_sheet_1_light, Res.drawable.onboarding_report_sheet_2_light)
            sheets.forEachIndexed { index, art ->
                Image(painterResource(art), null, Modifier.fillMaxSize().graphicsLayer {
                    val settle = onboardingPhase(progress, .02f + index * .07f, .24f + index * .07f)
                    translationX = (-18f + index * 18f).dp.toPx() * (1f - settle)
                    translationY = (-18f - index * 5f).dp.toPx() * (1f - settle)
                    rotationZ = (-7f + index * 7f) * (1f - settle)
                    alpha = settle
                }, contentScale = ContentScale.Fit)
            }
            Image(painterResource(if (dark) Res.drawable.onboarding_report_blank_dark else Res.drawable.onboarding_report_blank_light),
                null, Modifier.fillMaxSize().graphicsLayer {
                    val settle = onboardingPhase(progress, .24f, .46f)
                    translationX = 10.dp.toPx() * (1f - settle)
                    translationY = -14.dp.toPx() * (1f - settle)
                    rotationZ = 2.5f * (1f - settle)
                    alpha = settle
                }, contentScale = ContentScale.Fit)
            Canvas(Modifier.fillMaxSize()) {
                val scan = onboardingPhase(progress, .46f, .86f)
                val strength = sin(PI * scan).toFloat().coerceAtLeast(0f)
                // Coordinates follow the source document's body, below its engraved header.
                val sx = size.width / 240f
                val sy = size.height / 285f
                val lengths = listOf(117f, 117f, 117f, 117f, 117f, 117f, 87f, 49f)
                lengths.forEachIndexed { index, length ->
                    val write = (scan * 8f - index).coerceIn(0f, 1f)
                    val rowY = (100f + index * 12f) * sy
                    if (write > 0f) drawLine(ink, Offset(56f * sx, rowY), Offset((56f + length * write) * sx, rowY),
                        maxOf(.6.dp.toPx(), 1.4f * sx), cap = StrokeCap.Round)
                }
                val ready = onboardingPhase(progress, .84f, 1f)
                val center = Offset(211f * sx, 222f * sy)
                drawCircle(ink, 16f * sx, center, alpha = ready * .10f)
                drawArc(ink, -90f, 360f * ready, false,
                    Offset(center.x - 14f * sx, center.y - 14f * sy), Size(28f * sx, 28f * sy),
                    style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
                val check = Path().apply {
                    moveTo(204f * sx, 222f * sy); lineTo(209f * sx, 227f * sy); lineTo(219f * sx, 216f * sy)
                }
                val measure = PathMeasure().apply { setPath(check, false) }
                val drawn = Path()
                if (ready > 0f) {
                    measure.getSegment(0f, measure.length * ready, drawn, true)
                    drawPath(drawn, ink, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                }
                val y = (100f + 96f * scan) * sy
                drawRect(Brush.verticalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = strength * .12f)),
                    startY = y - 18f * sy, endY = y), Offset(53f * sx, y - 18f * sy), Size(126f * sx, 18f * sy))
                drawLine(ink, Offset(53f * sx, y), Offset(179f * sx, y),
                    strokeWidth = 1.dp.toPx(), alpha = strength * .7f)
            }
        }
    }
}
