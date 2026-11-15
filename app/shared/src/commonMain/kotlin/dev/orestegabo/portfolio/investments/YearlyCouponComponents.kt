package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun CouponYearHeader(year: Int, currentYear: Int, onYearChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onYearChange(year - 1) }, enabled = year > 1900) { AppIcon(Icons.Outlined.ChevronLeft, "Previous year") }
            AppText(year.toString(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            if (year != currentYear) TextButton(onClick = { onYearChange(currentYear) }) { AppText("This year") }
            IconButton(onClick = { onYearChange(year + 1) }, enabled = year < 9998) { AppIcon(Icons.Outlined.ChevronRight, "Next year") }
        }
        DetailNote(localized("Coupons due in {0}. Tap a payment to open its bond.").replace("{0}", year.toString()))
    }
}

@Composable
internal fun CouponYearTotals(totals: YearCouponTotals, currency: String, hasIncompleteTerms: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        DetailAmount(if (!hasIncompleteTerms) "Scheduled net coupons" else "Known scheduled net coupons", totals.scheduledNet, currency)
        DetailColumns(
            first = { DetailStatistic("Confirmed by you", totals.confirmed?.let { money(it, currency) } ?: "—") },
            second = { DetailStatistic("Marked automatically", totals.automatic?.let { money(it, currency) } ?: "—") })
        DetailNote("Automatic markings do not verify receipt. Scheduled coupons are expected income, not cash received.")
        HorizontalDivider()
    }
}

@Composable
internal fun YearCouponRow(coupon: YearCoupon, currency: String, openBond: (JsonRecord) -> Unit) {
    val description = "${localized("View bond details")}: ${privateFinancialValue(coupon.purchase.text("bondName"))} · ${detailDate(coupon.date)}"
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = localized("View bond details")) { openBond(coupon.purchase) }
        .semantics { contentDescription = description }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(detailDate(coupon.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FinancialText(coupon.purchase.text("bondName"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
            AppIcon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppText(coupon.state.label, style = MaterialTheme.typography.labelMedium,
            color = if (coupon.state == CouponReceiptState.CONFIRMED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        DetailValueRow("Expected net coupon", coupon.expectedNet?.let { money(it, currency) } ?: "—")
        if (coupon.recordedAmount != null) DetailValueRow("Recorded receipt amount", money(coupon.recordedAmount, currency))
        if (coupon.estimatedDate) DetailNote("Estimated coupon date")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}

@Composable
internal fun CouponPagination(visibleCount: Int, totalCount: Int, onLoadMore: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        FinancialText(localized("{0} of {1} coupons").replace("{0}", visibleCount.toString()).replace("{1}", totalCount.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (visibleCount < totalCount) TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { AppText("Load more coupons") }
    }
}

@Composable
internal fun NextCouponYearPreview(next: NextCouponYear, open: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val action = localized("View {0} coupons").replace("{0}", next.year.toString())
    Surface(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = action, onClick = open),
        shape = MaterialTheme.shapes.large, color = colors.primary.copy(alpha = .045f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = .15f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailNote("Next year with scheduled coupons")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppText(next.year.toString(), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                AppIcon(Icons.Outlined.ChevronRight, null, tint = colors.primary)
            }
            next.coupons.take(2).forEach { coupon ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText(detailDate(coupon.date), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    FinancialText(coupon.purchase.text("bondName"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FinancialText(money(coupon.expectedNet!!, coupon.currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (coupon.estimatedDate) DetailNote("Estimated coupon date")
                }
            }
            DetailNote("Preview of scheduled net payments, not confirmed receipts.")
        }
    }
}

@Composable
internal fun CouponYearEmptyState(year: Int, next: NextCouponYear?) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(Icons.Outlined.EventBusy, null, Modifier.size(32.dp), tint = colors.onSurfaceVariant)
        AppText(localized("No coupons due in {0}").replace("{0}", year.toString()), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        AppText(next?.let { localized("You have scheduled coupons in {0}. Preview them below.").replace("{0}", it.year.toString()) }
            ?: localized("Try another year. Pending purchases are not included."),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
