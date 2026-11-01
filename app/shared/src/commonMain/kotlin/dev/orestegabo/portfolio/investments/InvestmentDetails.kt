package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

@Composable
fun BondDetailsScreen(model: PortfolioModel, purchase: JsonRecord, engine: InvestmentEngine,
    imported: (JsonRecord) -> Unit, back: () -> Unit, edit: () -> Unit) {
    val currency = purchase.text("currency")
    val today = Today
    val terms = bondDetailTerms(purchase)
    val tracking = terms?.let { trackBond(it, today) }
    val next = nextDetailCoupon(purchase, model.couponReceipts, today)
    val active = purchase.text("status") == "active"
    val pending = purchase.text("status") == "submitted"
    var confirmDelete by remember(purchase.text("id")) { mutableStateOf(false) }
    var showDocuments by rememberSaveable(purchase.text("id")) { mutableStateOf(false) }
    var showTerms by rememberSaveable(purchase.text("id")) { mutableStateOf(false) }
    var period by rememberSaveable(purchase.text("id")) { mutableStateOf("Upcoming") }
    var allPayments by rememberSaveable(purchase.text("id"), period) { mutableStateOf(false) }
    LaunchedEffect(purchase.text("id")) {
        if (!model.couponTrackingAvailable && !model.notificationsLoading) model.refreshNotifications()
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { AppText("Delete purchase?") },
        text = { AppText("This removes ${purchase.text("bondName")} from your synchronized portfolio.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = {
            model.deletePurchase(purchase.text("id"), back); confirmDelete = false
        }) { AppText("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { AppText("Keep purchase") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "identity") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    InvestmentLogo(bond = true)
                    AppText(purchase.text("issuer").ifBlank { localized("Bonds") }, Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = .07f)) {
                        AppText(displayRecordValue(purchase.text("status")), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                FinancialText(purchase.text("bondName"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium,
                    modifier = Modifier.semantics { heading() })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FinancialText(purchase.text("isin"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { showDocuments = !showDocuments }) { AppIcon(Icons.Outlined.Description, "Documents") }
                }
            }
        }
        if (showDocuments) item(key = "source-documents") { DetailDocumentRows(model, purchase) }
        item(key = "principal") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailAmount(when { pending -> "Amount applied"; active -> "Bond principal"; else -> "Recorded principal" }, purchase.optDouble("faceValue", Double.NaN).takeIf { it.isFinite() }, currency)
                if (!pending && terms != null) DetailValueRow("Annual coupon rate", percent(terms.couponRate))
                HorizontalDivider()
            }
        }
        item(key = "next-coupon") {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .055f), shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .12f))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailHeading("Next coupon", next?.let { detailDate(it) })
                    if (next != null && terms != null) {
                        DetailAmount("Expected net coupon", terms.netCouponPayment(), currency, prominent = false)
                        AppText(when (today.daysUntil(next)) { 0 -> "Due today"; 1 -> "Tomorrow"; else -> "In ${today.daysUntil(next)} days" },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = .15f))
                        DetailValueRow("Gross per payment", money(terms.grossCouponPayment(), currency))
                        DetailValueRow("Withholding tax", percent(terms.taxRate))
                    } else AppText(when {
                        pending -> "Awaiting BNR confirmation"
                        !active -> "No upcoming payments for this holding"
                        terms == null -> "Coupon terms unavailable"
                        tracking?.schedule.isNullOrEmpty() && terms.maturityDate > today -> "Coupon dates unavailable"
                        else -> "No remaining coupons"
                    }, style = MaterialTheme.typography.titleMedium)
                    DetailNote("Scheduled income is not confirmation of payment. Dates and amounts follow your saved terms.")
                    DetailValueRow("Schedule", displayRecordValue(purchase.text("scheduleConfidence").ifBlank { "estimated" }))
                }
            }
        }
        if (needsPersonalConfirmation(purchase)) item(key = "confirmation") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PendingConfirmationAction(purchase, !model.busy) { model.confirmPendingPurchase(purchase) }
                PdfImportButton(model, engine, imported, bondOnly = true, targetPurchaseId = purchase.text("id"))
            }
        }
        if (!pending && terms != null && tracking != null) item(key = "maturity") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailHeading("Maturity timeline")
                AppText(detailDate(terms.maturityDate), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                LinearProgressIndicator(progress = { tracking.progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                DetailColumns(
                    first = { Column { DetailNote("Purchase date"); AppText(detailDate(terms.purchaseDate), style = MaterialTheme.typography.bodyMedium) } },
                    second = { DetailNote(when { terms.maturityDate <= today -> "Maturity reached"; today.daysUntil(terms.maturityDate) == 1 -> "1 day to maturity"; else -> "${today.daysUntil(terms.maturityDate)} days to maturity" }) })
                DetailValueRow("Principal at maturity", money(terms.faceValue, currency))
                DetailNote("Coupon rate is not yield to maturity. Redemption has not been verified by this timeline.")
                HorizontalDivider()
            }
        }
        if (!pending && tracking == null) calendarDate(purchase.text("maturityDate"))?.let { maturity ->
            item(key = "known-maturity") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailHeading("Maturity")
                    FinancialText(detailDate(maturity), style = MaterialTheme.typography.titleLarge)
                    DetailNote("Coupon terms unavailable")
                    HorizontalDivider()
                }
            }
        }
        if (!pending) item(key = "income") {
            val totals = couponReceiptTotals(purchase, model.couponReceipts, today)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DetailHeading("Income tracking")
                DetailColumns(
                    first = { DetailStatistic("Confirmed by you", if (model.couponTrackingAvailable) money(totals.confirmedByUser, currency) else "—") },
                    second = { DetailStatistic("Marked automatically", if (model.couponTrackingAvailable) money(totals.markedAutomatically, currency) else "—") })
                DetailNote("Automatic markings follow your settings; they do not verify payment into your account.")
                if (tracking != null) {
                    DetailValueRow("Scheduled to date", money(tracking.scheduledCouponsToDate, currency))
                    if (active) {
                        DetailValueRow("Accrued coupon", money(tracking.accruedCoupon, currency))
                        DetailValueRow("Remaining coupons", money(tracking.remainingCoupons, currency))
                    }
                }
                if (!model.couponTrackingAvailable) {
                    DetailNote(if (model.notificationsLoading) "Loading receipt status…" else "Receipt status unavailable")
                    TextButton(enabled = !model.notificationsLoading, onClick = model::refreshNotifications) { AppText("Refresh receipts") }
                }
                HorizontalDivider()
            }
        }
        if (!pending && tracking != null) {
            item(key = "payment-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailHeading("Payment ledger", "Expected amounts and recorded receipts stay separate.")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Upcoming", "Past").forEach { option ->
                            FilterChip(selected = period == option, onClick = { period = option }, label = { AppText(option) })
                        }
                    }
                }
            }
            val dates = if (period == "Upcoming") tracking.schedule.filter { it >= today } else tracking.schedule.filter { it < today }.reversed()
            if (dates.isEmpty()) item { DetailNote("No payments in this period") }
            items(if (allPayments) dates else dates.take(3), key = { "payment:$it" }) { date ->
                BondPaymentLedgerRow(model, purchase, date, today, tracking.netPerPayment)
            }
            if (dates.size > 3) item {
                TextButton(onClick = { allPayments = !allPayments }, modifier = Modifier.fillMaxWidth()) {
                    AppText(if (allPayments) "Show fewer payments" else "Show all ${dates.size} payments")
                }
            }
        }
        item(key = "management") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailDisclosure("Purchase details", showTerms) { showTerms = !showTerms }
                if (showTerms) { if (pending) PendingPurchaseFacts(purchase) else BondPurchaseFacts(purchase) }
                HorizontalDivider()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!pending) OutlinedButton(onClick = edit, enabled = !model.busy) { AppIcon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); AppText("Edit purchase") }
                    TextButton(onClick = { confirmDelete = true }, enabled = !model.busy) { AppText("Delete purchase", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun BondPaymentLedgerRow(model: PortfolioModel, purchase: JsonRecord, date: LocalDate, today: LocalDate, expected: Double) {
    val currency = purchase.text("currency")
    val receipt = model.couponReceipt(purchase.text("id"), date.toString())
    val state = couponReceiptState(date, today, model.couponTrackingAvailable, receipt)
    val recorded = recordedCouponAmount(receipt, currency)
    val icon = when (state) {
        CouponReceiptState.CONFIRMED -> Icons.Outlined.CheckCircle
        CouponReceiptState.AUTOMATIC -> Icons.Outlined.Autorenew
        CouponReceiptState.UPCOMING -> Icons.Outlined.Schedule
        else -> Icons.Outlined.RadioButtonUnchecked
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailColumns(first = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(detailDate(date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(icon, null, Modifier.size(16.dp), tint = if (state == CouponReceiptState.CONFIRMED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    AppText(state.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }, second = {
            DetailStatistic(if (recorded == null) "Expected net coupon" else "Recorded receipt amount", money(recorded ?: expected, currency))
        })
        if (recorded != null) DetailValueRow("Expected net coupon", money(expected, currency))
        if (date <= today && model.couponTrackingAvailable) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (receipt?.optBoolean("received") != true || state == CouponReceiptState.AUTOMATIC) TextButton(enabled = !model.busy,
                modifier = Modifier.semantics { contentDescription = "${localized("Confirm received")} ${detailDate(date)}" },
                onClick = { model.confirmCoupon(purchase.text("id"), date.toString(), true) }) { AppText("Confirm received") }
            if (receipt?.optBoolean("received") == true) TextButton(enabled = !model.busy,
                modifier = Modifier.semantics { contentDescription = "${localized("Mark not received")} ${detailDate(date)}" },
                onClick = { model.confirmCoupon(purchase.text("id"), date.toString(), false) }) { AppText("Mark not received") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}
