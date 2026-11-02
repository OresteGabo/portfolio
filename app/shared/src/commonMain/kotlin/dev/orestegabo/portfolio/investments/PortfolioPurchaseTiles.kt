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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun InvestmentEntry(title: String, subtitle: String, bond: Boolean, ticker: String = "", open: () -> Unit) {
    Surface(onClick = open, shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))) {
        ListItem(
            headlineContent = { AppText(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { FinancialText(subtitle, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { InvestmentLogo(bond = bond, ticker = ticker) },
            trailingContent = { AppIcon(Icons.Outlined.ChevronRight, "Details") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun BondPurchaseTile(model: PortfolioModel, purchase: JsonRecord, manage: () -> Unit) {
    var expanded by rememberSaveable(purchase.text("id")) { mutableStateOf(false) }
    val actions = LocalNativeActions.current
    var downloading by remember { mutableStateOf<String?>(null) }
    val pending = purchase.text("status") == "submitted"
    val next = nextDetailCoupon(purchase, model.couponReceipts, Today)
    Surface(shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))) {
        Column {
            ListItem(
                headlineContent = { AppText(purchase.text("bondName"), fontWeight = FontWeight.SemiBold) },
                supportingContent = { FinancialText("${money(purchase.optDouble("faceValue"), purchase.text("currency"))} · ${localized(purchase.text("status"))}",
                    style = MaterialTheme.typography.bodySmall) },
                leadingContent = { InvestmentLogo(bond = true) },
                trailingContent = { AppIcon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.semantics { stateDescription = localized(if (expanded) "Expanded" else "Collapsed") }
                    .clickable(onClickLabel = localized(if (expanded) "Hide bond summary" else "Show bond summary")) { expanded = !expanded }
            )
            AppAnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .055f)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (pending) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                AppText("Awaiting BNR confirmation", style = MaterialTheme.typography.bodySmall)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    next?.let { BondPreviewMetric("Next coupon", it.toString()) }
                                    if (purchase.has("couponRate")) BondPreviewMetric("Coupon rate", percent(purchase.optDouble("couponRate")))
                                }
                            }
                            if (purchase.text("maturityDate").isNotBlank()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
                                Fact("Maturity", purchase.text("maturityDate"))
                            }
                        }
                    }
                    val files = investmentEvidence(purchase, model.documents)
                    if (files.isEmpty()) Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(Icons.Outlined.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        AppText("No linked document", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    files.forEach { evidence ->
                        val document = evidence.document
                        val title = document.text("label")
                        Surface(shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Column(Modifier.width(38.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon(Icons.Outlined.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        AppText("PDF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    FinancialText(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    document.text("documentDate").takeIf { it.isNotBlank() }?.let {
                                        FinancialText(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                FilledTonalIconButton(enabled = !model.busy, onClick = { model.documentAction {
                                    downloading = document.text("id")
                                    try {
                                        if (shareInvestmentDocument(model, actions, document)) model.message = "PDF ready to share."
                                    } finally { downloading = null }
                                } }, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp)) {
                                    if (downloading == document.text("id")) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else AppIcon(Icons.Outlined.Download, "Download ${privateFinancialValue(title)}", Modifier.size(21.dp))
                                }
                            }
                        }
                    }
                    Button(onClick = manage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        AppText("View bond details", Modifier.weight(1f))
                        AppIcon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BondPreviewMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FinancialText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Persisted terms and costs, shared by the bond detail view. */
@Composable
fun BondPurchaseFacts(purchase: JsonRecord) {
    val currency = purchase.text("currency")
    listOf("isin" to "ISIN", "issuer" to "Issuer", "instrumentType" to "Instrument", "currency" to "Currency",
        "market" to "Market", "status" to "Status", "purchaseDate" to "Purchase date", "settlementDate" to "Settlement",
        "maturityDate" to "Maturity", "tenorYears" to "Tenor (years)", "broker" to "Broker",
        "accountReference" to "Account", "scheduleConfidence" to "Schedule", "firstCouponDate" to "First coupon")
        .forEach { (key, label) -> if (purchase.text(key).isNotBlank()) DetailValueRow(label, purchase.text(key)) }
    DetailValueRow("Bond principal", purchase.optDouble("faceValue", Double.NaN).takeIf { it.isFinite() }?.let { money(it, currency) } ?: "—")
    DetailValueRow("Price", "${purchase.optDouble("pricePercent", 100.0)}%")
    DetailValueRow("Fees", money(purchase.optDouble("feesPaid", 0.0), currency))
    DetailValueRow("Accrued interest paid", money(purchase.optDouble("accruedInterestPaid", 0.0), currency))
    DetailValueRow("Total cash cost", money(purchase.bondCashCost(), currency))
    DetailValueRow("Coupon rate", purchase.optDouble("couponRate", Double.NaN).takeIf { it.isFinite() }?.let(::percent) ?: "—")
    DetailValueRow("Withholding tax", purchase.optDouble("withholdingTaxRate", Double.NaN).takeIf { it.isFinite() }?.let(::percent) ?: "—")
    if (purchase.has("couponFrequency")) DetailValueRow("Payments per year", purchase.text("couponFrequency"))
    DetailValueRow("Annual net coupon", bondDetailTerms(purchase)?.let { money(purchase.netAnnualBondCoupon(), currency) } ?: "—")
    if(purchase.text("confirmationMethod")=="manual") DetailValueRow("Confirmation","Confirmed by you")
    if(purchase.text("confirmationMethod")=="document") DetailValueRow("Confirmation","Confirmation PDF uploaded")
    PurchaseSourceAndNotes(purchase)
}

@Composable
fun SharePurchaseTile(holding: JsonRecord, open: () -> Unit) {
    InvestmentEntry(holding.text("securityName").ifBlank { holding.text("ticker") },
        "${holding.text("shares")} shares · Total cost ${money(holding.optDouble("netAmountPayable"), holding.text("currency"))}",
        bond = false, ticker = holding.text("ticker"), open = open)
}

@Composable
internal fun SharePurchaseFacts(holding: JsonRecord) {
    listOf("tradeDate" to "Trade date", "settlementDate" to "Settlement", "status" to "Status",
        "shares" to "Shares", "isin" to "ISIN", "exchange" to "Exchange", "broker" to "Broker",
        "contractNote" to "Contract note", "cdsAccountNo" to "CDS account", "cdsReference" to "CDS reference",
        "tradeReportId" to "Trade report").forEach { (key, label) ->
        if (holding.text(key).isNotBlank()) DetailValueRow(label, holding.text(key))
    }
    listOf("pricePerShare" to "Price per share", "grossConsideration" to "Gross consideration",
        "brokerageCommission" to "Brokerage commission", "rseTransactionFee" to "RSE transaction fee",
        "csdTransactionLevy" to "CSD transaction levy", "cmaTransactionFee" to "CMA transaction fee",
        "totalCharges" to "Total charges", "netAmountPayable" to "Total cash cost").forEach { (key, label) ->
        if (holding.has(key)) DetailValueRow(label, money(holding.optDouble(key), holding.text("currency")))
    }
    PurchaseSourceAndNotes(holding)
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
