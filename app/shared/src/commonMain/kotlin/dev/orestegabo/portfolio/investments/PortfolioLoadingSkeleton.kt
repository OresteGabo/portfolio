package dev.orestegabo.portfolio.investments

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp

/** Only the initial authorized snapshot uses placeholders; refresh keeps real data visible. */
@Composable
internal fun PortfolioLoadingSkeleton() {
    val phase = if (LocalReducedMotion.current) null else {
        rememberInfiniteTransition(label = "Portfolio shimmer").animateFloat(
            initialValue = -1f, targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "Shimmer position",
        )
    }
    val description = localized("Loading your saved investments…")
    Column(Modifier.fillMaxSize().clearAndSetSemantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StructMark(Modifier.size(54.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PortfolioPlaceholder(phase, Modifier.fillMaxWidth().height(24.dp))
                PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.7f).height(14.dp))
            }
        }
        Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PortfolioPlaceholder(phase, Modifier.width(130.dp).height(16.dp))
            PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.72f).height(46.dp))
            PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.48f).height(16.dp))
        }
        PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.48f).height(24.dp))
        PortfolioPlaceholder(phase, Modifier.fillMaxWidth().height(168.dp))
        PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.55f).height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { PortfolioPlaceholder(phase, Modifier.weight(1f).height(36.dp)) }
        }
        repeat(3) { PortfolioPlaceholder(phase, Modifier.fillMaxWidth().height(88.dp)) }
    }
}

@Composable
private fun PortfolioPlaceholder(phase: State<Float>?, modifier: Modifier) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
    Box(modifier.clip(RoundedCornerShape(12.dp)).drawWithCache {
        onDrawBehind {
            drawRect(base)
            // Read animation state while drawing, without recomposing or relaying out the dashboard.
            phase?.value?.let { position ->
                val start = size.width * position
                drawRect(Brush.linearGradient(listOf(base, highlight, base),
                    start = Offset(start, 0f), end = Offset(start + size.width, size.height)))
            }
        }
    })
}
