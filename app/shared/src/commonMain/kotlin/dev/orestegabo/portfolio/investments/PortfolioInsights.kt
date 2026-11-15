package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.orestegabo.portfolio.design.StructColors
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

private enum class InsightStyle { ALLOCATION, INCOME, PAYMENT }

@Composable
private fun insightAccent(style: InsightStyle): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return when (style) {
        InsightStyle.ALLOCATION -> StructColors.Dashboard.allocationAccent.resolve(dark)
        InsightStyle.INCOME -> StructColors.Dashboard.incomeAccent.resolve(dark)
        InsightStyle.PAYMENT -> StructColors.Dashboard.paymentAccent.resolve(dark)
    }
}

@Composable
private fun InsightCard(title: String, style: InsightStyle, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = insightAccent(style)
    val palette = when (style) {
        InsightStyle.ALLOCATION -> StructColors.Dashboard.allocationGradient.resolve(dark)
        InsightStyle.INCOME -> StructColors.Dashboard.incomeGradient.resolve(dark)
        InsightStyle.PAYMENT -> StructColors.Dashboard.paymentGradient.resolve(dark)
    }
    val foreground = StructColors.Dashboard.insightForeground.resolve(dark)
    // One explicit shared height, scaled for accessibility, keeps every carousel edge aligned.
    val height = 236.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Surface(modifier.height(height), shape = RoundedCornerShape(26.dp), color = Color.Transparent, contentColor = foreground,
        border = BorderStroke(1.dp, accent.copy(alpha = if (dark) .2f else .16f))) {
        Box(Modifier.background(Brush.linearGradient(palette))) {
            Canvas(Modifier.matchParentSize().clipToBounds()) {
                val center = Offset(size.width * .97f, size.height * .12f)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .14f), Color.Transparent), center = center,
                    radius = size.width * .8f), radius = size.width * .8f, center = center)
                if (style == InsightStyle.PAYMENT) {
                    for (index in 0..3) {
                        val path = Path().apply {
                            moveTo(size.width * .45f, size.height + index * 18.dp.toPx())
                            cubicTo(size.width * .6f, size.height * .3f, size.width * .9f, size.height * 1.1f,
                                size.width * 1.1f, size.height * .35f + index * 14.dp.toPx())
                        }
                        drawPath(path, accent.copy(alpha = .08f), style = Stroke(1.dp.toPx()))
                    }
                } else {
                    for (index in 0..3) drawCircle(accent.copy(alpha = .055f), size.width * (.3f + index * .1f),
                        Offset(size.width * 1.02f, size.height * .95f), style = Stroke(1.dp.toPx()))
                }
            }
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                content()
            }
        }
    }
}

@Composable
internal fun AllocationCard(total: CurrencyInvestment, modifier: Modifier) {
    val fraction = if (total.investedCash > 0) (total.shareCashCost / total.investedCash).toFloat().coerceIn(0f, 1f) else 0f
    val foreground = LocalContentColor.current
    val hidden = LocalNumbersHidden.current
    InsightCard("Cash allocation", InsightStyle.ALLOCATION, modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (hidden) HiddenFinancialChart(Modifier.size(112.dp)) else CaptureRedactionRegion(Modifier.size(112.dp), masked = { HiddenFinancialChart(Modifier.fillMaxSize()) }) { Box(Modifier.fillMaxSize().semantics { contentDescription = localized("Shares ${percent(fraction.toDouble())}, bonds ${percent(if (total.investedCash > 0) 1.0 - fraction else 0.0)} of cash cost") }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                    val stroke = Stroke(13.dp.toPx(), cap = StrokeCap.Butt)
                    drawArc(foreground.copy(alpha = .08f), -90f, 360f, false, style = stroke)
                    if (total.investedCash > 0) {
                        drawArc(
                            StructColors.Dashboard.allocationGreen,
                            -90f,
                            360f,
                            false,
                            style = stroke
                        )
                        drawArc(
                            Brush.sweepGradient(
                                listOf(
                                    StructColors.Dashboard.gold,
                                    StructColors.Dashboard.allocationHighlight,
                                    StructColors.Dashboard.gold
                                )
                            ), -90f, fraction * 360f, false, style = stroke
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(if (total.investedCash > 0) "${(maxOf(fraction, 1f - fraction) * 100).roundToInt()}%" else "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AppText(if (total.investedCash <= 0) "No cost" else if (fraction >= .5f) "Shares" else "Bonds", style = MaterialTheme.typography.labelSmall)
                }
            }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AllocationLegend(
                    "Shares",
                    total.shareCashCost,
                    total.currency,
                    StructColors.Dashboard.gold
                )
                AllocationLegend(
                    "Bonds",
                    total.bondCashCost,
                    total.currency,
                    StructColors.Dashboard.allocationGreen
                )
            }
        }
        HorizontalDivider(color = insightAccent(InsightStyle.ALLOCATION).copy(alpha = .2f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Total cash cost", style = MaterialTheme.typography.labelMedium)
            FinancialText(money(total.investedCash, total.currency), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun AllocationLegend(label: String, cost: Double, currency: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(7.dp)) { drawCircle(color) }
            AppText(label, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = .65f))
        }
        FinancialText(money(cost, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InsightIcon(icon: ImageVector, accent: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = accent.copy(alpha = .12f)) {
        AppIcon(icon, null, Modifier.padding(13.dp).size(25.dp), tint = accent)
    }
}

@Composable
internal fun CouponIncomeCard(
    total: CurrencyInvestment,
    portfolio: PortfolioSnapshot,
    coupons: List<InvestmentCalendarEvent>,
    modifier: Modifier,
    openCalendar: (LocalDate) -> Unit,
) {
    val tracks = portfolio.investmentBonds(Today).filter { it.text("currency") == total.currency }
        .mapNotNull { it.terms()?.let { terms -> trackBond(terms, Today) } }
    val accent = insightAccent(InsightStyle.INCOME)
    val next = coupons.firstOrNull()
    InsightCard("Coupon income", InsightStyle.INCOME, modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Annual net" to total.netAnnualCoupon, "Accrued" to tracks.sumOf { it.accruedCoupon }).forEach { (label, amount) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText(label, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = .68f))
                    FinancialText((if (label == "Annual net" && total.estimatedPendingCouponCount > 0) "≈ " else "") + money(amount, total.currency), Modifier,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (label == "Annual net" && total.pendingAnnualCoupon > 0) {
                        FinancialText((if (total.estimatedPendingCouponCount > 0) "≈ " else "") + "${money(total.pendingAnnualCoupon,total.currency)} ${localized("pending")}",
                            style=MaterialTheme.typography.labelSmall,maxLines=2)
                    }
                }
            }
        }
        HorizontalDivider(color = accent.copy(alpha = .2f))
        if (next == null) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InsightIcon(Icons.Outlined.EventAvailable, accent)
                AppText("No upcoming coupons", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val amount = coupons.filter { it.date == next.date }.sumOf { it.amount ?: 0.0 }
            Row(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = localized("Open coupon in calendar")) { openCalendar(next.date) }
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = .12f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(next.date.monthLabel().take(3).uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
                        AppText(next.date.day.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText("Next coupon · ${next.date.year}", style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = .68f))
                    FinancialText(money(amount, total.currency), Modifier,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                AppIcon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = accent)
            }
        }
    }
}

/** Keep system icons legible as dashboard content scrolls beneath them. */
@Composable
internal fun MonthlyTargetCard(model: PortfolioModel, modifier: Modifier) {
    val progress = model.monthlyTargetProgress()
    InsightCard("Monthly target", InsightStyle.PAYMENT, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FinancialText(money(model.monthlyInvestmentTarget.toDouble(), "RWF"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            AppIcon(if (progress?.achieved == true) Icons.Outlined.CheckCircle else Icons.Outlined.Flag, null)
        }
        if (progress == null) {
            AppLoadingLine(model.busy, reserveSpace = true)
            if (!model.busy) AppText("Progress unavailable", style = MaterialTheme.typography.bodySmall)
        }
        else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!LocalNumbersHidden.current) CaptureRedactionRegion(Modifier.fillMaxWidth(), masked = { Spacer(Modifier.fillMaxSize()) }) {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            }
            else Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    AppText("Invested", style = MaterialTheme.typography.labelSmall)
                    FinancialText(money(progress.invested, "RWF"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    AppText("Remaining", style = MaterialTheme.typography.labelSmall)
                    FinancialText(money(progress.remaining, "RWF"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            AppText(if (progress.achieved) "${Today.monthLabel()} · Target reached" else Today.monthLabel(), style = MaterialTheme.typography.labelLarge)
            if (progress.pending > 0) FinancialText("${money(progress.pending, "RWF")} pending", style = MaterialTheme.typography.labelSmall)
        }
    }
}
