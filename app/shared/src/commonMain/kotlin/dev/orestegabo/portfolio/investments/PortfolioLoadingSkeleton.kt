package dev.orestegabo.portfolio.investments

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.orestegabo.portfolio.design.InziraSpacing
import dev.orestegabo.portfolio.design.StructColors

/** Initial authorized loading only. Refresh continues to show the real dashboard. */
@Composable
internal fun PortfolioLoadingSkeleton(openNotifications: () -> Unit = {}, unreadNotifications: Int = 0,
    showNotifications: Boolean = true, subtitle: String = "Your way forward") {
    val phase = if (LocalReducedMotion.current) null else rememberInfiniteTransition(label = "Portfolio shimmer").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "Shimmer position")
    val description = localized("Loading your saved investments…")
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val scale = LocalDensity.current.fontScale
    LazyColumn(Modifier.fillMaxSize().semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }, contentPadding = PaddingValues(bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(InziraSpacing.screen)) {
        item(key = "hero") {
            PortfolioHeroFrame(topInset, header = {
                PortfolioHeroHeader(showNotifications, subtitle, openNotifications, unreadNotifications) {
                    InziraLoadingMark(active = true, modifier = Modifier.size(22.dp))
                }
            }) {
                Column(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = portfolioBalanceAmountHeight()), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom) {
                            PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.7f).height(portfolioBalanceAmountHeight()))
                            Spacer(Modifier.width(8.dp))
                            PortfolioPlaceholder(phase, Modifier.width(24.dp).height(16.dp * scale))
                        }
                        PortfolioPriceStatus("")
                    }
                    PortfolioBalanceSurface {
                        if (portfolioMetricsUseColumns(maxWidth, scale)) Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LoadingMetric("Bond principal", Icons.Outlined.AccountBalance, phase, Modifier.weight(1f), column = true)
                            LoadingMetric("RSE equity value", Icons.Outlined.BarChart, phase, Modifier.weight(1f), column = true)
                            LoadingMetric("Annual net coupon", Icons.Outlined.Percent, phase, Modifier.weight(1f), column = true)
                        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LoadingMetric("Bond principal", Icons.Outlined.AccountBalance, phase, Modifier.fillMaxWidth())
                            LoadingMetric("RSE equity value", Icons.Outlined.BarChart, phase, Modifier.fillMaxWidth())
                            LoadingMetric("Annual net coupon", Icons.Outlined.Percent, phase, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        item(key = "allocation") {
            Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppText("Asset allocation", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    for (icon in listOf(Icons.Outlined.ChevronLeft, Icons.Outlined.ChevronRight)) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        AppIcon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = (maxWidth - 56.dp).coerceAtMost(340.dp)
                    LazyRow(userScrollEnabled = false, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(2) { LoadingAllocationCard(phase, Modifier.width(cardWidth)) }
                    }
                }
            }
        }
        item(key = "investment-heading") {
            Column(Modifier.padding(horizontal = 20.dp).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AppText("Your investments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        AppText("Past 12 months", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PortfolioPlaceholder(phase, Modifier.width(48.dp).height(16.dp * scale))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(Modifier.weight(1f), userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(3) { Box(Modifier.width(68.dp).height(48.dp), contentAlignment = Alignment.Center) {
                            PortfolioPlaceholder(phase, Modifier.fillMaxWidth().height(32.dp))
                        } }
                    }
                    PortfolioPlaceholder(phase, Modifier.size(48.dp))
                }
            }
        }
        item(key = "investment-section") {
            Box(Modifier.padding(horizontal = 20.dp).clearAndSetSemantics {}) {
                PortfolioPlaceholder(phase, Modifier.width(64.dp).height(16.dp * scale))
            }
        }
        items(3, key = { "investment:$it" }) {
            Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clearAndSetSemantics {},
                shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PortfolioPlaceholder(phase, Modifier.size(44.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.75f).height(20.dp * scale))
                        PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.55f).height(16.dp * scale))
                    }
                    PortfolioPlaceholder(phase, Modifier.width(10.dp).height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingMetric(label: String, icon: ImageVector, phase: State<Float>?, modifier: Modifier, column: Boolean = false) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = StructColors.Dashboard.worthAccent.resolve(dark)
    val labelColor = StructColors.Dashboard.worthMetricLabel.resolve(dark)
    val scale = LocalDensity.current.fontScale
    if (column) Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(icon, null, Modifier.size(20.dp), tint = accent)
        AppText(label, style = MaterialTheme.typography.labelSmall, minLines = 2, color = labelColor)
        PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.85f).height(24.dp * scale))
    } else Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(icon, null, Modifier.size(18.dp), tint = accent)
        if (scale > 1.3f) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
            PortfolioPlaceholder(phase, Modifier.width(112.dp).height(24.dp * scale))
        } else {
            AppText(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = labelColor)
            PortfolioPlaceholder(phase, Modifier.weight(1f).height(24.dp * scale))
        }
    }
}

@Composable
private fun LoadingAllocationCard(phase: State<Float>?, modifier: Modifier) {
    val scale = LocalDensity.current.fontScale
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
    Surface(modifier.height(236.dp * scale.coerceAtLeast(1f)), shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PortfolioPlaceholder(phase, Modifier.width(128.dp).height(20.dp * scale))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                        val stroke = Stroke(13.dp.toPx())
                        drawCircle(base, style = stroke)
                        phase?.value?.let { position ->
                            val start = size.width * position
                            drawCircle(Brush.linearGradient(listOf(base, highlight, base), Offset(start, 0f), Offset(start + size.width, size.height)), style = stroke)
                        }
                    }
                    PortfolioPlaceholder(phase, Modifier.width(36.dp).height(18.dp * scale))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    repeat(2) { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        PortfolioPlaceholder(phase, Modifier.fillMaxWidth(.55f).height(16.dp * scale))
                        PortfolioPlaceholder(phase, Modifier.fillMaxWidth().height(20.dp * scale))
                    } }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PortfolioPlaceholder(phase, Modifier.width(96.dp).height(16.dp * scale))
                PortfolioPlaceholder(phase, Modifier.width(64.dp).height(16.dp * scale))
            }
        }
    }
}

@Composable
private fun PortfolioPlaceholder(phase: State<Float>?, modifier: Modifier) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
    Box(modifier.clip(RoundedCornerShape(8.dp)).drawWithCache {
        onDrawBehind {
            drawRect(base)
            // Animation state is read during drawing, without recomposing the dashboard.
            phase?.value?.let { position ->
                val start = size.width * position
                drawRect(Brush.linearGradient(listOf(base, highlight, base), Offset(start, 0f), Offset(start + size.width, size.height)))
            }
        }
    })
}
