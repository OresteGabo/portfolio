package dev.orestegabo.portfolio.investments

import kotlin.math.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Hypothetical monthly model. Preserve the established simulator arithmetic and cash-flow order. */
object InvestmentProjection {
    private val defaults = """{"monthlyContribution":300000,"contributionPeriods":[{"id":"default","amount":300000,"startMonth":1,"endMonth":240}],"horizonYears":20,"startMonth":7,"startYear":2026,"tenorYears":7,"allowedTenors":[5,7,10,15,20,25],"tenorCouponRates":{"5":0.11,"7":0.115,"10":0.12,"15":0.129,"20":0.131,"25":0.1325},"annualCouponRate":0.115,"couponPaymentsPerYear":2,"reinvestmentRate":1,"auctionFillRate":0.67,"purchaseMinimum":100000,"purchaseCharge":11000}"""
    private val cycle = listOf(7, 10, 15, 20, 5, 7, 10, 25, 15, 10, 20, 7)
    private fun n(record: JsonRecord, key: String, fallback: Double = 0.0) = record.optDouble(key, fallback)
    private fun monthIndex(a: JsonRecord, date: String): Int? {
        val parts = date.split('-'); val year = parts.getOrNull(0)?.toIntOrNull() ?: return null; val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (year == 0 || month == 0) return null
        return (year - a.optInt("startYear")) * 12 + month - a.optInt("startMonth") + 1
    }
    fun startingLot(p: JsonRecord, a: JsonRecord): JsonRecord? {
        if (p.text("status") != "active" || n(p, "faceValue") <= 0 || n(p, "couponRate") < 0) return null
        val maturity = monthIndex(a, p.text("maturityDate"))?.takeIf { it >= 1 } ?: return null
        val date = p.text("settlementDate").ifEmpty { p.text("purchaseDate") }
        val purchaseMonth = monthIndex(a, date) ?: 0
        val dates = p.optJSONArray("couponDates") ?: JsonList()
        val schedule = (0 until dates.length()).mapNotNull { i -> val day = dates.getString(i); monthIndex(a, day)?.takeIf { it in 1..maturity }?.let { JsonRecord().put("date", day).put("month", it) } }
        val frequency = max(1.0, n(p, "couponFrequency", 2.0).takeUnless { it == 0.0 } ?: 2.0)
        val face = n(p, "faceValue"); val rate = n(p, "couponRate"); val netRate = rate * (1 - n(p, "withholdingTaxRate"))
        return JsonRecord().put("id", "actual-${p.text("id")}").put("purchaseMonth", purchaseMonth).put("purchaseDate", date).put("maturityMonth", maturity).put("maturityDate", p.text("maturityDate")).put("amount", face).put("cashCost", n(p, "amountInvested")).put("tenorYears", n(p, "tenorYears")).put("annualCouponRate", rate).put("netAnnualCouponRate", netRate).put("couponFrequency", frequency).put("couponMonths", schedule.map { it.optInt("month") }).put("couponSchedule", schedule).put("grossCouponAmount", n(p, "grossCouponAmount", cents(face * rate / frequency))).put("netCouponAmount", n(p, "netCouponAmount", cents(face * rate * (1 - n(p, "withholdingTaxRate")) / frequency)))
    }
    suspend fun project(input: JsonRecord): JsonRecord {
        val a = JsonRecord(defaults)
        input.optJSONObject("assumptions")?.element()?.forEach { (k, v) -> a.put(k, v) }
        val real = if (input.optBoolean("includeHoldings")) input.rows("purchases").mapNotNull { startingLot(it, a) } else null
        val rows = calculate(a, input.rows("injections"), real)
        return JsonRecord().put("rows", rows).put("summary", summarize(rows))
    }
    suspend fun calculate(a: JsonRecord, injections: List<JsonRecord> = emptyList(), starting: List<JsonRecord>? = null): List<JsonRecord> {
        val months = max(1, jsRound(n(a, "horizonYears") * 12).toInt())
        require(months <= 1200) { "Use a horizon of at most 100 years." }
        require(n(a, "purchaseMinimum") > 0) { "Purchase minimum must be positive." }
        val periods = a.rows("contributionPeriods").ifEmpty { listOf(JsonRecord().put("amount", n(a, "monthlyContribution")).put("startMonth", 1).put("endMonth", months)) }
        val frequency = max(1.0, n(a, "couponPaymentsPerYear")); val interval = 12 / frequency
        val fill = n(a, "auctionFillRate").coerceIn(0.0, 1.0)
        val tenors = a.optJSONArray("allowedTenors")?.let { array -> (0 until array.length()).mapNotNull { array.getString(it).toDoubleOrNull()?.toInt() } }.orEmpty().ifEmpty { listOf(5, 7, 10, 15, 20, 25) }
        fun calendar(month: Int): Pair<Int, Int> { val index = a.optInt("startYear") * 12 + a.optInt("startMonth") - 1 + month - 1; return floor(index / 12.0).toInt() to ((index % 12 + 12) % 12 + 1) }
        fun modeledDate(month: Int): String { val (year, m) = calendar(month); return "$year-${m.toString().padStart(2, '0')}-05" }
        fun couponRate(tenor: Int): Double {
            val configured = a.optJSONObject("tenorCouponRates")?.optDouble(tenor.toString()) ?: Double.NaN
            if (configured.isFinite() && configured > 0) return configured
            if (tenor == a.optInt("tenorYears")) return n(a, "annualCouponRate")
            return JsonRecord(defaults).getJSONObject("tenorCouponRates").optDouble(tenor.toString(), n(a, "annualCouponRate"))
        }
        fun lot(month: Int, amount: Double, tenor: Int, cost: Double): JsonRecord {
            val rate = couponRate(tenor).coerceIn(.1065, .135); val maturity = month + tenor * 12
            return JsonRecord().put("id", "modeled-$month").put("purchaseMonth", month).put("purchaseDate", modeledDate(month)).put("maturityMonth", maturity).put("maturityDate", modeledDate(maturity)).put("amount", amount).put("cashCost", cost).put("tenorYears", tenor).put("annualCouponRate", rate).put("netAnnualCouponRate", rate * (1 - .05)).put("couponFrequency", frequency)
        }
        val real = starting?.filter { n(it, "amount") > 0 && n(it, "maturityMonth") >= 1 }
        var active = real.orEmpty().filter { n(it, "purchaseMonth") <= 1 }.toMutableList()
        var pending = real.orEmpty().filter { n(it, "purchaseMonth") > 1 }
        val openingCost = active.sumOf { n(it, "cashCost", n(it, "amount")) }
        var cash = 0.0; var contributions = 0.0; var coupons = 0.0; var reinvested = 0.0
        return (1..months).map { month ->
            currentCoroutineContext().ensureActive()
            val added = pending.filter { n(it, "purchaseMonth") == month.toDouble() }
            active.addAll(added); pending = pending.filter { n(it, "purchaseMonth") != month.toDouble() }
            val realPurchase = if (month == 1) openingCost else added.sumOf { n(it, "cashCost", n(it, "amount")) }
            val (year, calendarMonth) = calendar(month)
            val offset = (year - 2026) * 12 + (calendarMonth - 7)
            val tenor = cycle[(offset % cycle.size + cycle.size) % cycle.size]
            val eligible = tenor in tenors
            val openingPortfolio = active.sumOf { n(it, "amount") }; val openingCash = cash
            val personal = periods.sumOf { if (month >= n(it, "startMonth") && month <= n(it, "endMonth")) max(0.0, n(it, "amount")) else 0.0 }
            val monthlyInjections = injections.filter { n(it, "month") == month.toDouble() }; val injected = monthlyInjections.sumOf { n(it, "amount") }
            val payments = active.flatMap { l ->
                val dates: List<String?> = if (l.has("couponSchedule")) {
                    l.rows("couponSchedule").filter { n(it, "month") == month.toDouble() && month <= n(l, "maturityMonth") }.distinctBy { it.text("date") }.map { it.text("date") }
                } else if (interval % 1 == 0.0 && month > n(l, "purchaseMonth") && month <= n(l, "maturityMonth") && (month - n(l, "purchaseMonth")) % interval == 0.0) listOf(null) else emptyList()
                dates.map { date -> JsonRecord().put("lotId", l.text("id")).put("purchaseDate", l.text("purchaseDate")).also { if (date != null) it.put("couponDate", date) }.put("amountInvested", n(l, "amount")).put("grossCouponAmount", n(l, "grossCouponAmount", n(l, "amount") * (n(l, "annualCouponRate") / n(l, "couponFrequency")))).put("couponAmount", n(l, "netCouponAmount", n(l, "amount") * (n(l, "netAnnualCouponRate") / n(l, "couponFrequency")))) }
            }
            val payment = payments.sumOf { n(it, "couponAmount") }
            val matured = active.filter { n(it, "maturityMonth") == month.toDouble() }.sumOf { n(it, "amount") }
            active = active.filter { n(it, "maturityMonth") != month.toDouble() }.toMutableList()
            val reinvestment = payment * n(a, "reinvestmentRate")
            val available = cents(openingCash + personal + injected + reinvestment + matured)
            val minimum = n(a, "purchaseMinimum")
            val intended = if (eligible) floor((available + .001) / minimum) * minimum else 0.0
            val filled = floor((intended * fill + .001) / minimum) * minimum
            val purchase = min(intended, filled); val charge = if (purchase > 0) max(0.0, n(a, "purchaseCharge")) else 0.0
            val newLot = if (purchase > 0) lot(month, purchase, tenor, purchase + charge) else null
            if (newLot != null) active.add(newLot)
            cash = cents(available - purchase)
            val portfolio = active.sumOf { n(it, "amount") }
            contributions += personal + injected + realPurchase + charge; coupons += payment; reinvested += reinvestment
            val annual = active.sumOf { n(it, "amount") * n(it, "netAnnualCouponRate") }
            JsonRecord().put("month", month).put("year", ceil(month / 12.0).toInt()).put("monthInYear", (month - 1) % 12 + 1).put("calendarMonth", calendarMonth).put("calendarYear", year).put("openingPortfolio", openingPortfolio).put("openingCashBalance", openingCash).put("personalContribution", personal).put("cashInjection", injected).put("cashInjectionLabels", monthlyInjections.map { it.text("label") }).put("realBondPurchase", realPurchase).put("purchaseCharge", charge).put("auctionTenorYears", tenor).put("auctionEligible", eligible).put("couponPayment", payment).put("couponPayments", payments).put("reinvestedCoupon", reinvestment).put("maturedPrincipal", matured).put("availableCash", available).put("intendedBondBid", intended).put("unfilledBondBid", intended - purchase).put("newBondPurchase", purchase).put("newBondPurchaseLot", newLot).put("activeBondCount", active.size).put("closingCashBalance", cash).put("closingPortfolio", portfolio).put("totalAccountValue", portfolio + cash).put("totalContributions", contributions).put("totalCoupons", coupons).put("totalReinvested", reinvested).put("annualBondPassiveIncome", annual).put("annualPassiveIncome", annual).put("monthlyPassiveIncome", annual / 12)
        }
    }
    fun summarize(rows: List<JsonRecord>): JsonRecord {
        val last = rows.lastOrNull() ?: JsonRecord()
        val summary = JsonRecord()
        for ((output, field) in listOf("finalPortfolio" to "closingPortfolio", "finalCashBalance" to "closingCashBalance", "finalAccountValue" to "totalAccountValue", "totalContributions" to "totalContributions", "totalCoupons" to "totalCoupons", "totalReinvested" to "totalReinvested", "annualBondPassiveIncome" to "annualBondPassiveIncome", "annualPassiveIncome" to "annualPassiveIncome", "monthlyPassiveIncome" to "monthlyPassiveIncome")) summary.put(output, n(last, field))
        for ((key, threshold) in listOf("milestone50m" to 50_000_000.0, "milestone100m" to 100_000_000.0, "milestone200m" to 200_000_000.0)) summary.put(key, rows.firstOrNull { n(it, "totalAccountValue") >= threshold }?.optInt("month"))
        return summary.put("passiveIncomeCrossoverYear", rows.firstOrNull { n(it, "annualPassiveIncome") > n(it, "personalContribution") * 12 }?.let { ceil(n(it, "month") / 12).toInt() })
    }
}
