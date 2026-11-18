package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.orestegabo.portfolio.design.StructColors

@Composable
internal fun PortfolioHero(showNotifications: Boolean, subtitle: String, totals: List<CurrencyValuation>, topInset: Dp, openNotifications: () -> Unit, unreadNotifications: Int, stale: Boolean, priceDate: String, notice: @Composable () -> Unit) {
    PortfolioHeroFrame(topInset, header = {
        PortfolioHeroHeader(showNotifications, subtitle, openNotifications, unreadNotifications)
    }, notice = notice) {
        if (totals.isEmpty()) Spacer(Modifier.height(46.dp))
        else totals.forEach { total -> PortfolioBalance(total, stale, priceDate) }
    }
}

/** Shared geometry keeps initial loading aligned with the populated dashboard. */
@Composable
internal fun PortfolioHeroFrame(topInset: Dp, header: @Composable () -> Unit,
    notice: @Composable () -> Unit = { Spacer(Modifier.height(0.dp)) }, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val glow = StructColors.Dashboard.balanceGlow.resolve(dark)
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.radialGradient(
                colors = listOf(glow.copy(alpha = if (dark) .10f else .09f), Color.Transparent),
                center = Offset(size.width * .85f, size.height * .32f), radius = size.width * .85f))
        }
        Column(Modifier.fillMaxWidth().smoothContentSize().padding(start = 20.dp, end = 20.dp, top = 16.dp + topInset),
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { header(); notice() }
            content()
        }
    }
}

@Composable
internal fun PortfolioHeroHeader(showNotifications: Boolean, subtitle: String,
    openNotifications: () -> Unit, unreadNotifications: Int) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DashboardBrandMark(Modifier.size(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            InziraWordmark(Modifier.height(26.dp), onDark = dark)
            AppText(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showNotifications) NotificationBell(unreadNotifications, openNotifications, tint = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun PortfolioBalance(valuation: CurrencyValuation, stale: Boolean, priceDate: String) {
    val total = valuation.cost
    val priceLabel = when {
        valuation.unpricedShares > 0 && valuation.pricedShares == 0 -> "Shares at purchase cost"
        valuation.unpricedShares > 0 -> "Some shares at purchase cost"
        valuation.pricedShares > 0 -> if (stale) "Cached closing prices" else "Latest closing prices"
        else -> "Bonds at principal value"
    }
    val priceBasis = localized(priceLabel) + if (valuation.pricedShares > 0 && calendarDate(priceDate) != null) " · $priceDate" else ""
    var showBasis by remember { mutableStateOf(false) }

    if (showBasis) {
        AlertDialog(
            onDismissRequest = { showBasis = false },
            title = { AppText("About this estimate") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    AppText("$priceBasis\n\nTracked investments, including pending requests at recorded amounts. Bonds use principal value; shares use the latest available RSE closing price. Unpriced shares use purchase cash cost. Cash balances, liabilities, dividends and selling fees are excluded. Annual coupons are shown separately.")
                    HorizontalDivider()
                    Fact("Confirmed annual net coupon",money(total.confirmedAnnualCoupon,total.currency))
                    Fact("Pending annual net coupon",(if (total.estimatedPendingCouponCount > 0) "≈ " else "") + money(total.pendingAnnualCoupon,total.currency))
                    if (total.estimatedPendingCouponCount > 0) AppText("Missing pending rates use public treasury coupons by tenor. Between known tenors, rates are interpolated. Your saved withholding tax applies. Estimates do not change contracts or payment dates.",style=MaterialTheme.typography.bodySmall)
                    if (total.unavailablePendingCouponCount > 0) AppText("Some pending coupons could not be estimated and are excluded.",style=MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showBasis = false }) { AppText("Done") }
            }
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppFadeIn(valuation.estimatedNetWorth to total.currency, Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = localized("Valuation basis details")) { showBasis = true }
                    .semantics { contentDescription = localized("Valuation basis details") },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    FinancialText(money(valuation.estimatedNetWorth, total.currency).removePrefix("${total.currency} "),
                        modifier = Modifier.weight(1f, fill = false).alignByBaseline(), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum", letterSpacing = (-1).sp),
                        color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium,
                        maxLines = 1, softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 48.sp, stepSize = .5.sp))
                    AppText(total.currency, Modifier.padding(start = 8.dp).alignByBaseline(),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stale || valuation.unpricedShares > 0) AppText(priceLabel,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PortfolioBalanceSurface {
            val longestAmount = listOf(total.bondFaceValue, valuation.shareValue, total.netAnnualCoupon)
                .maxOf { money(it, total.currency).removePrefix("${total.currency} ").length }
            // Keep three columns at standard phone widths after the card's horizontal padding.
            val columns = portfolioMetricsUseColumns(maxWidth, LocalDensity.current.fontScale, longestAmount)
            if (columns) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MoneyMetric("Bond principal", total.bondFaceValue, total.currency, Icons.Outlined.AccountBalance, Modifier.weight(1f), column = true)
                MoneyMetric("RSE equity value", valuation.shareValue, total.currency, Icons.Outlined.BarChart, Modifier.weight(1f), column = true)
                MoneyMetric("Annual net coupon", total.netAnnualCoupon, total.currency, Icons.Outlined.Percent,
                    Modifier.weight(1f), estimated = total.estimatedPendingCouponCount > 0, column = true)
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val stacked = LocalDensity.current.fontScale > 1.3f
                MoneyMetric("Bond principal", total.bondFaceValue, total.currency, Icons.Outlined.AccountBalance, Modifier.fillMaxWidth(), stacked = stacked)
                MoneyMetric("RSE equity value", valuation.shareValue, total.currency, Icons.Outlined.BarChart, Modifier.fillMaxWidth(), stacked = stacked)
                MoneyMetric("Annual net coupon", total.netAnnualCoupon, total.currency, Icons.Outlined.Percent,
                    Modifier.fillMaxWidth(), estimated = total.estimatedPendingCouponCount > 0, stacked = stacked)
            }
        }
    }
}

/** The balance stays outside a panel whose upper edge fades into the page. */
@Composable
internal fun PortfolioBalanceSurface(content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Surface(shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp), color = Color.Transparent,
        contentColor = StructColors.Dashboard.worthText.resolve(dark)) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                drawRect(Brush.linearGradient(StructColors.Dashboard.worthGradient.resolve(dark)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, StructColors.Fixed.black), endY = 40.dp.toPx()),
                    blendMode = BlendMode.DstIn)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 20.dp), content = content)
        }
    }
}

internal fun portfolioMetricsUseColumns(width: Dp, fontScale: Float, longestAmount: Int = 0) =
    width >= 280.dp && fontScale <= 1.1f && longestAmount <= 12

@Composable
private fun MoneyMetric(label: String, value: Double, currency: String, icon: ImageVector, modifier: Modifier,
                        estimated: Boolean = false, stacked: Boolean = false, column: Boolean = false) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = StructColors.Dashboard.worthAccent.resolve(dark)
    val labelColor = StructColors.Dashboard.worthMetricLabel.resolve(dark)
    val amount = (if (estimated) "≈ " else "") + money(value, currency).removePrefix("$currency ")
    if (column) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(icon, null, Modifier.size(20.dp), tint = accent)
            AppText(label, style = MaterialTheme.typography.labelSmall, minLines = 2,
                color = labelColor)
            AppFadeIn(amount, Modifier.fillMaxWidth()) {
                FinancialText(amount, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 16.sp, stepSize = .5.sp))
            }
        }
        return
    }
    val columnLayout = stacked || amount.length > 15
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(icon, null, Modifier.size(18.dp), tint = accent)
        if (columnLayout) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
                AppFadeIn(amount) {
                    FinancialText(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            AppText(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = labelColor)
            AppFadeIn(amount, Modifier.weight(1f)) {
                FinancialText(amount, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 18.sp, stepSize = .5.sp))
            }
        }
    }
}
