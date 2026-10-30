package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Clock

private data class InvestmentToolResults(val outlook: BondCashFlowOutlook, val fees: PurchaseFeeAnalysis)

@Composable
internal fun InvestmentToolsScreen(model: PortfolioModel, openCalendar: (LocalDate) -> Unit, openHistory: () -> Unit) {
    var section by rememberSaveable { mutableStateOf("Cash flows") }
    var requested by remember(model.user?.text("id")) { mutableStateOf(false) }
    LaunchedEffect(model.user?.text("id"), model.busy, model.portfolioSyncing, model.historyComplete) {
        if (!model.historyComplete && !model.busy && !model.portfolioSyncing && !requested) {
            requested = true
            model.loadInvestmentHistory()
        }
    }
    val today = bondSubscriptionDate(Clock.System.now())
    val snapshot = model.portfolio
    val complete = model.historyComplete
    val results by produceState<InvestmentToolResults?>(null, snapshot, complete, today) {
        value = null
        if (complete) value = withContext(Dispatchers.Default) {
            InvestmentToolResults(snapshot.bondCashFlowOutlook(today), snapshot.purchaseFeeAnalysis(today))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ChoiceRow(listOf("Cash flows", "Buying fees"), section) { section = it } }
        if (!model.historyComplete) item {
            if (model.busy || model.portfolioSyncing || !requested) LoadingState()
            else EmptyState("Investment history unavailable", EmptyArtwork.PORTFOLIO, compact = true,
                actionLabel = "Try again", onAction = { model.error = null; model.loadInvestmentHistory(force = true) })
        } else if (results == null) item { LoadingState() }
        else if (section == "Cash flows") {
            val outlook = results!!.outlook
            item { Panel {
                AppText("Bond cash-flow outlook", fontWeight = FontWeight.Bold)
                AppText("${outlook.from} – ${outlook.untilExclusive.minus(DatePeriod(days = 1))}", style = MaterialTheme.typography.bodySmall)
                AppText("Scheduled from saved active bonds, not income received. The current month starts today. Principal repayments are not profit.", style = MaterialTheme.typography.bodySmall)
                AppText("Recorded coupon rates and withholding taxes stay unchanged. No future purchases, reinvestment, dividends or market-price assumptions are included. Payment timing is not guaranteed.", style = MaterialTheme.typography.bodySmall)
                AppText("Saved confirmed dates take precedence. Other coupon dates are estimates from your bond terms.", style = MaterialTheme.typography.bodySmall)
            } }
            if (outlook.issues.isNotEmpty()) item { InsightIssues(outlook.issues) }
            if (outlook.months.isEmpty()) item {
                EmptyState("No active bond cash flows to forecast", EmptyArtwork.BONDS, compact = true,
                    actionLabel = "View purchases", onAction = openHistory)
            }
            items(outlook.months, key = { "${it.currency}:${it.month}" }) { month -> Panel {
                AppText("${month.month.monthLabel()} ${month.month.year} · ${month.currency}", fontWeight = FontWeight.SemiBold)
                Fact("Scheduled gross coupons", money(month.grossCoupons, month.currency))
                Fact("Withholding on coupons", money(month.withholding, month.currency))
                Fact("Scheduled net coupons", money(month.netCoupons, month.currency))
                Fact("Principal due", money(month.principal, month.currency))
                if (month.flows.any { it.estimatedDate }) AppText("Includes estimated coupon dates", style = MaterialTheme.typography.labelSmall)
                if (month.flows.isEmpty()) AppText("No scheduled payments this month", style = MaterialTheme.typography.bodySmall)
                else TextButton(onClick = { openCalendar(month.flows.first().date) }) { AppText("View payment dates") }
            } }
        } else {
            val analysis = results!!.fees
            item { Panel {
                AppText("Recorded purchase fees", fontWeight = FontWeight.Bold)
                AppText("Completed acquisitions through {0}.".let { localized(it).replace("{0}", today.toString()) }, style = MaterialTheme.typography.bodySmall)
                AppText("Includes recorded purchases even when later sold or matured. Pending and future purchases are excluded. This is acquisition history, not current portfolio value.", style = MaterialTheme.typography.bodySmall)
                AppText("Fees are already included in cash cost and are not added again. Fee rate means recorded buying charges divided by trade consideration; accrued interest is separate. Selling, custody and unrecorded charges are excluded.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = openHistory) { AppText("View purchases") }
            } }
            if (analysis.issues.isNotEmpty()) item { InsightIssues(analysis.issues) }
            if (analysis.currencies.isEmpty()) item {
                EmptyState("No complete purchase costs available", EmptyArtwork.PORTFOLIO, compact = true)
            }
            items(analysis.currencies, key = { it.currency }) { totals -> Panel {
                AppText(totals.currency, fontWeight = FontWeight.SemiBold)
                Fact("Purchases included", totals.purchases.size.toString())
                Fact("Trade consideration", money(totals.consideration, totals.currency))
                Fact("Accrued interest paid", money(totals.accruedInterest, totals.currency))
                Fact("Bond buying fees", money(totals.bondFees, totals.currency))
                Fact("Share buying fees", money(totals.equityFees, totals.currency))
                Fact("Total buying fees", money(totals.fees, totals.currency))
                Fact("Fees / consideration", totals.feeRate?.let(::percent) ?: "—")
                Fact("Total cash cost", money(totals.cashCost, totals.currency))
            } }
        }
    }
}

@Composable
private fun InsightIssues(issues: List<InsightIssue>) = Panel {
    AppText("Some records need attention", fontWeight = FontWeight.SemiBold)
    AppText("Missing or inconsistent values are not treated as zero. Check these records before relying on the totals.", style = MaterialTheme.typography.bodySmall)
    issues.forEach { issue ->
        Fact(issue.name.ifBlank { localized("Purchase") }, when (issue.reason) {
            InsightIssueReason.INVALID_RECORD -> "Check purchase date, maturity, currency and principal"
            InsightIssueReason.INCOMPLETE_COUPON_TERMS -> "Coupon rate, tax or frequency is missing or invalid"
            InsightIssueReason.UNAVAILABLE_SCHEDULE -> "Coupon dates unavailable"
            InsightIssueReason.INCOMPLETE_COSTS -> "Purchase costs are incomplete or inconsistent"
        })
    }
}
