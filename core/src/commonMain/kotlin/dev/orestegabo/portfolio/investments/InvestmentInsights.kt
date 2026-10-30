package dev.orestegabo.portfolio.investments

import kotlinx.datetime.*

/** Read-only views over persisted purchases. These are neither cash receipts nor investment recommendations. */
enum class InsightIssueReason { INVALID_RECORD, INCOMPLETE_COUPON_TERMS, UNAVAILABLE_SCHEDULE, INCOMPLETE_COSTS }
data class InsightIssue(val purchaseId: String, val name: String, val reason: InsightIssueReason)
enum class BondCashFlowKind { COUPON, PRINCIPAL }
data class BondCashFlow(val purchaseId: String, val name: String, val currency: String, val date: LocalDate,
    val kind: BondCashFlowKind, val amount: Double, val grossCoupon: Double = 0.0, val estimatedDate: Boolean = false)
data class BondCashFlowMonth(val month: LocalDate, val currency: String, val grossCoupons: Double,
    val netCoupons: Double, val principal: Double, val flows: List<BondCashFlow>) {
    val withholding get() = moneyTotal(listOf(grossCoupons, -netCoupons))
}
data class BondCashFlowOutlook(val from: LocalDate, val untilExclusive: LocalDate,
    val months: List<BondCashFlowMonth>, val issues: List<InsightIssue>)

/** Twelve calendar buckets include the remaining part of the current month; today is included.
 * Principal is redemption at recorded face value, not income. No reinvestment or future purchases. */
fun PortfolioSnapshot.bondCashFlowOutlook(asOf: LocalDate, monthCount: Int = 12): BondCashFlowOutlook {
    require(monthCount in 1..120)
    val first = LocalDate(asOf.year, asOf.month, 1)
    val end = first.plus(DatePeriod(months = monthCount))
    val flows = mutableListOf<BondCashFlow>()
    val issues = mutableListOf<InsightIssue>()
    val currencies = mutableSetOf<String>()
    for (p in purchases.filter { it.text("status") == "active" }) {
        val bought = calendarDate(p.text("purchaseDate"))
        val maturity = calendarDate(p.text("maturityDate"))
        val face = p.validAmount("faceValue")
        val currency = p.text("currency")
        fun issue(reason: InsightIssueReason) { issues += InsightIssue(p.text("id"), p.text("bondName"), reason) }
        if (bought == null || maturity == null || face == null || face <= 0 || maturity <= bought || !currency.validCurrency()) {
            issue(InsightIssueReason.INVALID_RECORD); continue
        }
        if (bought > asOf || maturity < asOf) continue
        currencies += currency
        if (maturity < end) flows += BondCashFlow(p.text("id"), p.text("bondName"), currency, maturity, BondCashFlowKind.PRINCIPAL, roundedMoney(face))
        // Do not silently interpret a missing rate/tax/frequency as zero or invent an exemption.
        val terms = p.terms()
        if (terms == null || p.validAmount("couponRate") == null || p.validAmount("withholdingTaxRate") == null || !p.has("couponFrequency")) {
            issue(InsightIssueReason.INCOMPLETE_COUPON_TERMS); continue
        }
        val dates = couponSchedule(terms)
        if (dates.isEmpty() && terms.couponRate > 0) { issue(InsightIssueReason.UNAVAILABLE_SCHEDULE); continue }
        val estimated = p.text("scheduleConfidence") != "confirmed" || terms.confirmedDates.isEmpty()
        if (terms.couponRate > 0) for (date in dates.filter { it >= asOf && it < end }) {
            flows += BondCashFlow(p.text("id"), p.text("bondName"), currency, date, BondCashFlowKind.COUPON,
                terms.netCouponPayment(), terms.grossCouponPayment(), estimated)
        }
    }
    val sorted = flows.sortedWith(compareBy({ it.date }, { it.currency }, { it.purchaseId }, { it.kind.ordinal }))
    val months = (0 until monthCount).flatMap { index ->
        val month = first.plus(DatePeriod(months = index))
        currencies.sorted().map { currency ->
            val rows = sorted.filter { it.currency == currency && it.date.year == month.year && it.date.month == month.month }
            BondCashFlowMonth(month, currency, moneyTotal(rows.map { it.grossCoupon }),
                moneyTotal(rows.filter { it.kind == BondCashFlowKind.COUPON }.map { it.amount }),
                moneyTotal(rows.filter { it.kind == BondCashFlowKind.PRINCIPAL }.map { it.amount }), rows)
        }
    }
    return BondCashFlowOutlook(asOf, end, months, issues)
}

enum class PurchaseFeeKind { BOND, EQUITY }
data class PurchaseFeeLine(val purchaseId: String, val name: String, val currency: String, val date: LocalDate,
    val kind: PurchaseFeeKind, val consideration: Double, val accruedInterest: Double, val fees: Double, val cashCost: Double)
data class CurrencyPurchaseFees(val currency: String, val purchases: List<PurchaseFeeLine>) {
    val consideration = moneyTotal(purchases.map { it.consideration })
    val accruedInterest = moneyTotal(purchases.map { it.accruedInterest })
    val bondFees = moneyTotal(purchases.filter { it.kind == PurchaseFeeKind.BOND }.map { it.fees })
    val equityFees = moneyTotal(purchases.filter { it.kind == PurchaseFeeKind.EQUITY }.map { it.fees })
    val fees = moneyTotal(purchases.map { it.fees })
    val cashCost = moneyTotal(purchases.map { it.cashCost })
    /** Recorded charges / trade consideration, excluding accrued interest and the fees themselves. */
    val feeRate = consideration.takeIf { it > 0 }?.let { fees / it }
}
data class PurchaseFeeAnalysis(val currencies: List<CurrencyPurchaseFees>, val issues: List<InsightIssue>)

/** Acquisition history, not current holdings valuation. Sold/matured acquisitions remain historical costs;
 * submitted/cancelled/future/aggregate rows never become completed transactions. No fees inferred from defaults. */
fun PortfolioSnapshot.purchaseFeeAnalysis(asOf: LocalDate): PurchaseFeeAnalysis {
    require(recentPurchaseIds == null && recentHoldingIds == null) { "Fee analysis requires complete purchase history." }
    val rows = mutableListOf<PurchaseFeeLine>()
    val issues = mutableListOf<InsightIssue>()
    fun invalid(p: JsonRecord, name: String) { issues += InsightIssue(p.text("id"), name, InsightIssueReason.INCOMPLETE_COSTS) }
    for (p in purchases.filter { it.text("status") in setOf("active", "matured", "sold") }) {
        val date = calendarDate(p.text("purchaseDate"))
        if (date != null && date > asOf) continue
        val face = p.validAmount("faceValue"); val price = p.validAmount("pricePercent")
        val accrued = p.validAmount("accruedInterestPaid"); val fees = p.validAmount("feesPaid")
        if (date == null || !p.text("currency").validCurrency() || face == null || face <= 0 || price == null || price <= 0 || accrued == null || fees == null) {
            invalid(p, p.text("bondName")); continue
        }
        val consideration = decimalAmount(listOf(face, price), 100)
        rows += PurchaseFeeLine(p.text("id"), p.text("bondName"), p.text("currency"), date, PurchaseFeeKind.BOND,
            consideration, accrued, fees, p.bondCashCost())
    }
    for (p in holdings.filter { it.text("status") in setOf("active", "sold") }) {
        val date = calendarDate(p.text("tradeDate"))
        if (date != null && date > asOf) continue
        val gross = p.validAmount("grossConsideration"); val fees = p.validAmount("totalCharges"); val payable = p.validAmount("netAmountPayable")
        if (date == null || !p.text("currency").validCurrency() || p.text("id").startsWith("aggregate:") || gross == null || gross <= 0 || fees == null || payable == null ||
            kotlin.math.abs(moneyTotal(listOf(gross, fees, -payable))) > .02) {
            invalid(p, p.text("securityName").ifBlank { p.text("ticker") }); continue
        }
        rows += PurchaseFeeLine(p.text("id"), p.text("securityName").ifBlank { p.text("ticker") }, p.text("currency"), date,
            PurchaseFeeKind.EQUITY, roundedMoney(gross), 0.0, roundedMoney(fees), roundedMoney(payable))
    }
    return PurchaseFeeAnalysis(rows.groupBy { it.currency }.entries.sortedBy { it.key }.map { (currency, entries) ->
        CurrencyPurchaseFees(currency, entries.sortedWith(compareByDescending<PurchaseFeeLine> { it.date }.thenBy { it.purchaseId }))
    }, issues)
}

private fun String.validCurrency() = matches(Regex("[A-Z]{3}"))
private fun JsonRecord.validAmount(key: String): Double? = optDouble(key).takeIf { has(key) && !isNull(key) && it.isFinite() && it >= 0 }
private fun moneyTotal(values: List<Double>) = decimalAmount(emptyList(), addends = values)
