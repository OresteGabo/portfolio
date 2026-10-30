package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
internal fun UnlockScreen(model: PortfolioModel, openLegal: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(colours.background).clipToBounds(), contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 600.dp
        UnlockHexagons(Modifier.fillMaxSize())
        Column(Modifier.widthIn(max = 460.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            UnlockLogo(Modifier.fillMaxWidth().height(if (compact) 152.dp else 204.dp), compact)
            Spacer(Modifier.height(if (compact) 18.dp else 28.dp))
            AppText(if (model.verifyingSession) "Device unlocked" else "Portfolio locked", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            AppText(if (model.verifyingSession) "Checking your server session. Your investments stay protected." else "Your saved sign-in is protected on this device.", Modifier.widthIn(max = 300.dp),
                style = MaterialTheme.typography.bodyMedium, color = colours.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(if (compact) 24.dp else 32.dp))
            Button(onClick = model::unlock, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                AppIcon(when (model.unlockLabel) {
                    "Face ID" -> Icons.Outlined.Face
                    "Touch ID", "Fingerprint" -> Icons.Outlined.Fingerprint
                    else -> Icons.Outlined.Lock
                }, null)
                Spacer(Modifier.width(8.dp))
                AppText(if (model.busy) "Unlocking…" else "Unlock with ${localized(model.unlockLabel)}", textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = model::usePassword, enabled = !model.busy || model.verifyingSession, modifier = Modifier.heightIn(min = 48.dp)) { AppText("Use password instead") }
            TextButton(onClick = openLegal, modifier = Modifier.heightIn(min = 48.dp)) { AppText("Legal & privacy") }
        }
    }
}

/** Quiet honeycomb outlines become progressively lighter toward the top of the screen. */
@Composable
private fun UnlockHexagons(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier.clearAndSetSemantics {}) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val radius = 36.dp.toPx()
        val stepX = sqrt(3f) * radius
        val stepY = radius * 1.5f
        val columns = ceil(size.width / stepX).toInt() + 2
        val rows = ceil(size.height / stepY).toInt() + 2
        for (row in 0..rows) for (column in -1..columns) {
            val cx = column * stepX + if (row % 2 == 0) 0f else stepX / 2
            val cy = row * stepY
            val fade = ((cy / size.height - .08f) / .92f).coerceIn(0f, 1f).pow(1.8f)
            val path = Path().apply {
                for (corner in 0..5) {
                    val angle = (corner * 60 - 90) * PI.toFloat() / 180
                    val x = cx + cos(angle) * (radius - 2.dp.toPx())
                    val y = cy + sin(angle) * (radius - 2.dp.toPx())
                    if (corner == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            if ((row * 3 + column) % 9 == 0) drawPath(path, ink.copy(alpha = .018f * fade))
            drawPath(path, ink.copy(alpha = .105f * fade), style = Stroke(.7.dp.toPx()))
        }
    }
}

/** The complete brand mark sits within the upper half of a circular seal. */
@Composable
private fun UnlockLogo(modifier: Modifier, compact: Boolean) {
    val ink = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            if (size.width <= 20.dp.toPx() || size.height <= 16.dp.toPx()) return@Canvas
            val radius = min(size.width / 2 - 10.dp.toPx(), size.height - 16.dp.toPx())
            val baseline = size.height - 10.dp.toPx()
            val centre = Offset(size.width / 2, baseline)
            val topLeft = Offset(centre.x - radius, baseline - radius)
            val circle = Size(radius * 2, radius * 2)
            drawArc(Brush.radialGradient(listOf(ink.copy(alpha = .08f), ink.copy(alpha = .025f)), centre, radius),
                180f, 180f, true, topLeft, circle)
            drawArc(ink.copy(alpha = .24f), 180f, 180f, false, topLeft, circle, style = Stroke(1.dp.toPx()))
            drawLine(ink.copy(alpha = .12f), Offset(centre.x - radius, baseline), Offset(centre.x + radius, baseline), 1.dp.toPx())
        }
        StructMark(Modifier.padding(bottom = 22.dp).size(if (compact) 84.dp else 108.dp))
    }
}
