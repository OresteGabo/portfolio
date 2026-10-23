package dev.orestegabo.portfolio.investments

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
private fun PurchaseTile(
    title: String, subtitle: String, expanded: Boolean, toggle: () -> Unit,
    logo: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f))) {
        Column {
            ListItem(
                headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
                leadingContent = logo,
                trailingContent = { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }.clickable(onClickLabel = if (expanded) "Collapse purchase" else "Expand purchase", onClick = toggle)
            )
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    content()
                }
            }
        }
    }
}

@Composable
fun BondPurchaseTile(purchase: JsonRecord, manage: () -> Unit) {
    var expanded by rememberSaveable(purchase.text("id")) { mutableStateOf(false) }
    val currency = purchase.text("currency")
    PurchaseTile(purchase.text("bondName"),
        "${money(purchase.optDouble("faceValue", 0.0), currency)} · ${purchase.text("status")}",
        expanded, { expanded = !expanded }, logo = { InvestmentLogo(bond = true) }) {
        BondPurchaseFacts(purchase)
        purchase.terms()?.let { terms ->
            val tracking = trackBond(terms, Today)
            HorizontalDivider()
            Fact("Next coupon", tracking.nextCoupon?.toString() ?: "No remaining coupons")
            Fact("Net per payment", money(tracking.netPerPayment, currency))
            Fact("Scheduled to date", money(tracking.couponsPaid, currency))
            Fact("Accrued coupon", money(tracking.accruedCoupon, currency))
            Fact("Remaining coupons", money(tracking.remainingCoupons, currency))
            Text("Coupon schedule · ${purchase.text("scheduleConfidence").ifBlank { "Estimated" }}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            tracking.schedule.forEach { date -> Fact(date.toString(), money(tracking.netPerPayment, currency)) }
        }
        OutlinedButton(onClick = manage, modifier = Modifier.fillMaxWidth()) { Text("Manage purchase") }
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
    PurchaseSourceAndNotes(purchase)
}

@Composable
fun SharePurchaseTile(holding: JsonRecord) {
    var expanded by rememberSaveable(holding.text("id")) { mutableStateOf(false) }
    val currency = holding.text("currency")
    PurchaseTile(listOf(holding.text("securityName"), holding.text("ticker")).filter { it.isNotBlank() }.joinToString(" · "),
        "${holding.text("shares")} shares · Cost ${money(holding.optDouble("netAmountPayable", 0.0), currency)}",
        expanded, { expanded = !expanded }, logo = { InvestmentLogo(ticker = holding.text("ticker")) }) {
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
    if (record.text("sourceUrl").isNotBlank()) TextButton(onClick = { actions.openUrl(record.text("sourceUrl")) }) { Text("Source document") }
}
