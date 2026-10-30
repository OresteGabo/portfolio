package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
private fun PurchaseTile(
    title: String, subtitle: String, expanded: Boolean, toggle: () -> Unit,
    pending: Boolean = false,
    logo: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(shape = RoundedCornerShape(22.dp), color = if (pending) MaterialTheme.colorScheme.onSurface.copy(alpha = .07f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f))) {
        Column {
            ListItem(
                headlineContent = { AppText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                supportingContent = { FinancialText(subtitle, style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Box(Modifier.alpha(if (pending) .5f else 1f)) { logo() } },
                trailingContent = { AppIcon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent,
                    headlineColor = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.semantics { stateDescription = localized(if (expanded) "Expanded" else "Collapsed") }.clickable(onClickLabel = localized(if (expanded) "Collapse purchase" else "Expand purchase"), onClick = toggle)
            )
            AppAnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    content()
                }
            }
        }
    }
}

@Composable
fun BondPurchaseTile(purchase: JsonRecord, uploadConfirmation: () -> Unit, manualConfirmation: (() -> Unit)? = null, confirming: Boolean = false, evidence: @Composable () -> Unit = {}, manage: () -> Unit) {
    var expanded by rememberSaveable(purchase.text("id")) { mutableStateOf(false) }
    val currency = purchase.text("currency")
    val pending = purchase.text("status") == "submitted"
    PurchaseTile(purchase.text("bondName"),
        "${money(purchase.optDouble("faceValue", 0.0), currency)} · ${if (pending) "Pending" else localized(purchase.text("status"))}",
        expanded, { expanded = !expanded }, pending = pending, logo = { InvestmentLogo(bond = true) }) {
        evidence()
        if (pending) PendingPurchaseFacts(purchase) else BondPurchaseFacts(purchase)
        if (!pending) purchase.terms()?.let { terms ->
            val tracking = trackBond(terms, Today)
            HorizontalDivider()
            Fact("Next coupon", tracking.nextCoupon?.toString() ?: "No remaining coupons")
            Fact("Net per payment", money(tracking.netPerPayment, currency))
            Fact("Scheduled to date", money(tracking.scheduledCouponsToDate, currency))
            Fact("Accrued coupon", money(tracking.accruedCoupon, currency))
            Fact("Remaining coupons", money(tracking.remainingCoupons, currency))
            AppText("Coupon schedule · ${localized(purchase.text("scheduleConfidence").ifBlank { "Estimated" })}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            tracking.schedule.forEach { date -> Fact(date.toString(), money(tracking.netPerPayment, currency)) }
        }
        if(pending && manualConfirmation!=null) PendingConfirmationAction(purchase,!confirming,manualConfirmation)
        OutlinedButton(onClick = if (needsPersonalConfirmation(purchase)) uploadConfirmation else manage, modifier = Modifier.fillMaxWidth()) { AppText(if (needsPersonalConfirmation(purchase)) "Upload confirmation" else "Manage purchase") }
        if (needsPersonalConfirmation(purchase)) TextButton(onClick = manage, modifier = Modifier.fillMaxWidth()) { AppText("Details") }
    }
}

/** The inline disclosure and management page show identical saved terms and costs. */
@Composable
fun BondPurchaseFacts(purchase: JsonRecord) {
    val currency = purchase.text("currency")
    listOf("isin" to "ISIN", "issuer" to "Issuer", "instrumentType" to "Instrument", "currency" to "Currency",
        "market" to "Market", "status" to "Status", "purchaseDate" to "Purchase date", "settlementDate" to "Settlement",
        "maturityDate" to "Maturity", "tenorYears" to "Tenor (years)", "broker" to "Broker",
        "accountReference" to "Account", "scheduleConfidence" to "Schedule", "firstCouponDate" to "First coupon")
        .forEach { (key, label) -> if (purchase.text(key).isNotBlank()) Fact(label, purchase.text(key)) }
    Fact("Bond principal", money(purchase.optDouble("faceValue", 0.0), currency))
    Fact("Price", "${purchase.optDouble("pricePercent", 100.0)}%")
    Fact("Fees", money(purchase.optDouble("feesPaid", 0.0), currency))
    Fact("Accrued interest paid", money(purchase.optDouble("accruedInterestPaid", 0.0), currency))
    Fact("Total cash cost", money(purchase.bondCashCost(), currency))
    Fact("Coupon rate", percent(purchase.optDouble("couponRate", 0.0)))
    Fact("Withholding tax", percent(purchase.optDouble("withholdingTaxRate", 0.0)))
    if (purchase.has("couponFrequency")) Fact("Payments per year", purchase.text("couponFrequency"))
    Fact("Annual net coupon", money(purchase.netAnnualBondCoupon(), currency))
    if(purchase.text("confirmationMethod")=="manual") Fact("Confirmation","Confirmed by you")
    if(purchase.text("confirmationMethod")=="document") Fact("Confirmation","Confirmation PDF uploaded")
    PurchaseSourceAndNotes(purchase)
}

@Composable
fun SharePurchaseTile(holding: JsonRecord, evidence: @Composable () -> Unit = {}) {
    var expanded by rememberSaveable(holding.text("id")) { mutableStateOf(false) }
    val currency = holding.text("currency")
    PurchaseTile(listOf(holding.text("securityName"), holding.text("ticker")).filter { it.isNotBlank() }.joinToString(" · "),
        "${holding.text("shares")} shares · Cost ${money(holding.optDouble("netAmountPayable", 0.0), currency)}",
        expanded, { expanded = !expanded }, logo = { InvestmentLogo(ticker = holding.text("ticker")) }) {
        evidence()
        listOf("ticker" to "Ticker", "isin" to "ISIN", "exchange" to "Exchange", "currency" to "Currency",
            "status" to "Status", "tradeDate" to "Trade date", "settlementDate" to "Settlement", "shares" to "Shares",
            "broker" to "Broker", "contractNote" to "Contract note", "cdsAccountNo" to "CDS account",
            "cdsReference" to "CDS reference", "tradeReportId" to "Trade report")
            .forEach { (key, label) -> if (holding.text(key).isNotBlank()) Fact(label, holding.text(key)) }
        listOf("pricePerShare" to "Price per share", "grossConsideration" to "Gross consideration",
            "brokerageCommission" to "Brokerage commission", "rseTransactionFee" to "RSE transaction fee",
            "csdTransactionLevy" to "CSD transaction levy", "cmaTransactionFee" to "CMA transaction fee",
            "totalCharges" to "Total charges", "netAmountPayable" to "Total cash cost")
            .forEach { (key, label) -> if (holding.has(key)) Fact(label, money(holding.optDouble(key, 0.0), currency)) }
        PurchaseSourceAndNotes(holding)
    }
}

@Composable
private fun PurchaseSourceAndNotes(record: JsonRecord) {
    val actions = LocalNativeActions.current
    if (record.text("notes").isNotBlank()) Fact("Notes", record.text("notes"))
    if (record.text("sourceUrl").isNotBlank()) TextButton(onClick = { actions.openUrl(record.text("sourceUrl")) }) { AppText("Source document") }
}

@Composable
fun PendingPurchaseFacts(purchase: JsonRecord) {
    Fact("Status", "Awaiting BNR confirmation")
    Fact("Amount applied", money(purchase.optDouble("faceValue", 0.0), purchase.text("currency")))
    Fact("Commission", money(purchase.optDouble("feesPaid", 0.0), purchase.text("currency")))
    Fact("Total paid", money(purchase.optDouble("faceValue", 0.0) + purchase.optDouble("feesPaid", 0.0), purchase.text("currency")))
    Fact("Recorded", purchase.text("purchaseDate"))
    Fact("Expected settlement", purchase.text("settlementDate"))
    Fact("Maturity", purchase.text("maturityDate"))
    if(purchase.optBoolean("publishedTermsAvailable")) {
        HorizontalDivider()
        AppText("Published issuance terms",fontWeight=FontWeight.SemiBold)
        purchase.optJSONObject("manualConfirmation")?.let { Fact("Security code",it.text("isin")) }
        Fact("Coupon rate",percent(purchase.optDouble("couponRate")))
        Fact("First coupon",purchase.text("firstCouponDate"))
        val dates=purchase.optJSONArray("couponDates") ?: JsonList()
        var showDates by rememberSaveable(purchase.text("id")) { mutableStateOf(false) }
        TextButton(onClick={ showDates=!showDates }) { AppText("Coupon payment dates");Spacer(Modifier.width(6.dp));AppText(dates.length().toString()) }
        if(showDates)for(index in 0 until dates.length())Fact((index+1).toString(),dates.getString(index))
        if(!purchase.optBoolean("canConfirmWithoutPdf"))AppText("A personal PDF is needed to confirm the final price.",style=MaterialTheme.typography.bodySmall)
    }
}
