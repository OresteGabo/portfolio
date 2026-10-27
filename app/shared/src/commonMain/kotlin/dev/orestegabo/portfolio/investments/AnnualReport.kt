package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

internal const val ANNUAL_REPORT_CATEGORY = "Annual reports"

internal fun annualReportDocuments(snapshot: PortfolioSnapshot, today: LocalDate): List<JsonRecord> {
    val first = (snapshot.purchases.mapNotNull { calendarDate(it.text("purchaseDate"))?.year } +
        snapshot.holdings.mapNotNull { calendarDate(it.text("tradeDate"))?.year })
        .filter { it in 1900..today.year }.minOrNull() ?: today.year
    return (today.year downTo first).map { year ->
        JsonRecord().put("id", "generated-annual-$year").put("annualReportYear", year)
            .put("category", ANNUAL_REPORT_CATEGORY).put("label", "Annual report · $year")
            .put("issuer", "Struct Capital").put("originalFileName", "Struct-Capital-Annual-$year.pdf")
            .put("documentDate", minOf(today, LocalDate(year, 12, 31)).toString())
            .put("description", if (year == today.year) "Year to date · Generated on download" else "Full calendar year · Generated on download")
    }
}

internal data class AnnualReportScope(
    val start: LocalDate, val end: LocalDate, val cutoff: LocalDate,
    val bonds: List<JsonRecord>, val shares: List<JsonRecord>, val receipts: List<JsonRecord>,
    val closingBonds: List<JsonRecord>, val closingShares: List<JsonRecord>, val uncertain: List<JsonRecord>,
)

internal fun annualReportScope(data: PortfolioReportData, year: Int): AnnualReportScope {
    require(year in 1900..data.today.year) { "Invalid report year." }
    require(data.snapshot.recentPurchaseIds == null && data.snapshot.recentHoldingIds == null) { "Annual reports require complete investment records." }
    val start = LocalDate(year, 1, 1)
    val end = LocalDate(year, 12, 31)
    val cutoff = minOf(end, data.today)
    fun JsonRecord.during(key: String) = calendarDate(text(key))?.let { it in start..cutoff } == true
    fun JsonRecord.before(key: String) = calendarDate(text(key))?.let { it <= cutoff } == true
    val bonds = data.snapshot.purchases.filter { it.before("purchaseDate") }
    val shares = data.snapshot.holdings.filter { it.before("tradeDate") }
    // There is no disposal/status-change history: never invent when a sold holding left the portfolio.
    val uncertain = bonds.filter { it.text("status") !in setOf("active", "submitted", "cancelled", "matured") ||
        (it.text("status") in setOf("active", "matured") && calendarDate(it.text("maturityDate")) == null) } +
        shares.filter { it.text("status") !in setOf("active", "submitted", "cancelled") }
    return AnnualReportScope(start, end, cutoff,
        bonds.filter { it.during("purchaseDate") }, shares.filter { it.during("tradeDate") },
        data.couponReceipts.orEmpty().filter { it.during("dueDate") },
        bonds.filter { it.text("status") in setOf("active", "matured") &&
            (calendarDate(it.text("settlementDate")) ?: calendarDate(it.text("purchaseDate")))?.let { it <= cutoff } == true &&
            calendarDate(it.text("maturityDate"))?.let { date -> date > cutoff } == true },
        shares.filter { it.text("status") == "active" }, uncertain)
}

/** Last official exchange close within this calendar year; never a future quote or a contract price. */
internal fun annualClosingPrice(history: JsonRecord?, quotes: JsonRecord?, ticker: String, scope: AnnualReportScope): StockPricePoint? =
    stockPriceHistory(history, quotes, ticker, "all", scope.cutoff)
        .lastOrNull { !it.contract && it.date >= scope.start }

internal fun generateAnnualReport(data: PortfolioReportData, year: Int, histories: Map<String, JsonRecord> = emptyMap()): ByteArray {
    val scope = annualReportScope(data, year)
    val fr = data.language == "fr"
    fun t(en: String, french: String) = if (fr) french else en
    fun cash(value: Double, currency: String): String {
        if (!value.isFinite()) return t("Unavailable", "Indisponible")
        val decimals = currency != "RWF"
        val units = (abs(value) * if (decimals) 100 else 1).roundToLong()
        val whole = (if (decimals) units / 100 else units).toString().reversed().chunked(3).joinToString(if (fr) " " else ",").reversed()
        return "$currency ${if (value < 0) "-" else ""}$whole" +
            if (decimals) (if (fr) "," else ".") + (units % 100).toString().padStart(2, '0') else ""
    }
    val pdf = ReportPdf(t("STRUCT CAPITAL / ANNUAL REPORT $year", "STRUCT CAPITAL / RAPPORT ANNUEL $year"),
        "$year | ${scope.start} - ${scope.cutoff}", fr)
    pdf.heading(t("Annual investment report", "Rapport annuel d'investissement"), true)
    pdf.paragraph("${data.email} | ${t("Generated", "Genere le")} ${data.today}")
    pdf.paragraph("${t("Reporting period", "Periode du rapport")}: ${scope.start} - ${scope.end}")
    if (scope.cutoff < scope.end) pdf.paragraph(t("YEAR TO DATE - Activity and valuation stop on ${scope.cutoff}; the rest of this year is not included.",
        "ANNEE EN COURS - L'activite et la valorisation s'arretent au ${scope.cutoff} ; le reste de l'annee est exclu."))
    pdf.paragraph(t("Only purchases dated within this period appear as annual transactions. Positions carried forward from earlier years are included only in the closing portfolio estimate. Currencies are kept separate.",
        "Seuls les achats dates dans cette periode figurent dans les operations annuelles. Les positions des annees precedentes figurent uniquement dans l'estimation du portefeuille de cloture. Les devises restent separees."))
    pdf.heading(t("Portfolio at period end", "Portefeuille en fin de periode"))
    pdf.paragraph(t("Valuation date: ${scope.cutoff}. Reconstructed from saved records, not a frozen historical statement. Current saved statuses and quantities may not reproduce later edits or earlier confirmations. Bonds use outstanding principal, not market prices; cash, dividends, liabilities and realized gains are excluded.",
        "Date de valorisation : ${scope.cutoff}. Reconstitution des donnees enregistrees, pas un releve historique fige. Statuts et quantites actuels peuvent ne pas refleter les modifications ulterieures ou confirmations anterieures. Les obligations utilisent le capital restant, pas un cours de marche ; liquidites, dividendes, dettes et gains realises sont exclus."))
    val currencies = (scope.closingBonds + scope.closingShares + scope.uncertain).map { it.text("currency") }.distinct().sorted()
    if (currencies.isEmpty()) pdf.paragraph(t("No closing positions identified in the saved records.", "Aucune position de cloture identifiee dans les donnees enregistrees."))
    val priceArchive = mutableListOf<JsonRecord>()
    currencies.forEach { currency ->
        val bonds = scope.closingBonds.filter { it.text("currency") == currency }
        val shares = scope.closingShares.filter { it.text("currency") == currency }
        var shareValue = 0.0
        var complete = scope.uncertain.none { it.text("currency") == currency }
        shares.forEach { share ->
            val ticker = share.text("ticker")
            val price = if (currency == "RWF") annualClosingPrice(histories[ticker], data.quotes, ticker, scope) else null
            val quantity = share.optDouble("shares", Double.NaN)
            if (price == null || !quantity.isFinite() || quantity < 0) complete = false
            else {
                shareValue += quantity * price.price
                priceArchive += JsonRecord().put("ticker", ticker).put("date", price.date.toString()).put("closing", price.price)
            }
            pdf.record("$ticker / ${share.text("securityName")}", listOf(
                t("Shares", "Actions") to share.text("shares"),
                t("Official close / date", "Cloture officielle / date") to (price?.let { "${cash(it.price, currency)} / ${it.date}" } ?: t("Unavailable within this year", "Indisponible pour cette annee")),
                t("Estimated share value", "Valeur estimee des actions") to (price?.takeIf { quantity.isFinite() && quantity >= 0 }?.let { cash(quantity * it.price, currency) } ?: t("Unavailable", "Indisponible"))))
        }
        val principal = bonds.sumOf { it.optDouble("faceValue", Double.NaN) }
        if (!principal.isFinite()) complete = false
        pdf.summaryMetrics(listOf(
            ReportMetric(t("Outstanding bond principal", "Capital obligataire restant"), cash(principal, currency)),
            ReportMetric(t("Closing portfolio estimate", "Estimation du portefeuille de cloture"), if (complete) cash(principal + shareValue, currency) else t("Unavailable", "Indisponible"))))
    }
    pdf.paragraph(t("Share prices are the last available official close within the reporting year, on or before the valuation date; each price date is shown. Missing prices are not replaced with today's prices or purchase costs. Missing disposal history or prices make the full closing estimate unavailable.",
        "Les cours sont les dernieres clotures officielles disponibles dans l'annee, au plus tard a la date de valorisation ; chaque date est indiquee. Aucun cours actuel ni cout d'achat ne remplace un cours manquant. Sans historique de cession ou cours, l'estimation totale est indisponible."))
    pdf.newPage()
    pdf.heading(t("Transactions in $year", "Operations de $year"), true)
    pdf.paragraph(t("Dates: ${scope.start} - ${scope.cutoff}. Statuses below are the current saved statuses, not historical confirmations. No transactions outside this interval are included.",
        "Dates : ${scope.start} - ${scope.cutoff}. Les statuts sont ceux actuellement enregistres, pas des confirmations historiques. Aucune operation hors de cet intervalle n'est incluse."))
    if (scope.bonds.isEmpty() && scope.shares.isEmpty()) pdf.paragraph(t("No purchases recorded in this period.", "Aucun achat enregistre sur cette periode."))
    scope.bonds.sortedBy { it.text("purchaseDate") }.forEach { row -> pdf.record(row.text("bondName"), listOf(
        t("Purchase date", "Date d'achat") to row.text("purchaseDate"), t("Saved status", "Statut enregistre") to row.text("status"),
        t("Principal", "Capital") to cash(row.optDouble("faceValue"), row.text("currency")),
        t("Purchase cash cost", "Cout d'achat") to cash(row.bondCashCost(), row.text("currency")))) }
    scope.shares.sortedBy { it.text("tradeDate") }.forEach { row -> pdf.record("${row.text("ticker")} / ${row.text("securityName")}", listOf(
        t("Trade date", "Date d'achat") to row.text("tradeDate"), t("Saved status", "Statut enregistre") to row.text("status"),
        t("Shares", "Actions") to row.text("shares"), t("Purchase cash cost", "Cout d'achat") to cash(row.optDouble("netAmountPayable"), row.text("currency")))) }
    if (scope.receipts.isEmpty()) {
        pdf.gap()
        pdf.paragraph(t("Coupon records: no receipt records available in this period.", "Coupons : aucune reception disponible sur cette periode."))
    } else {
        pdf.gap(24.0)
        pdf.ensure(150.0)
        pdf.heading(t("Coupon records in $year", "Coupons de $year"), true)
        pdf.paragraph(t("Only due dates within the reporting period are listed. Receipt flags are saved confirmations, not bank verification or a statement of cash received during the year.",
            "Seules les echeances de la periode sont listees. Les indicateurs de reception sont des confirmations enregistrees, pas une verification bancaire ni un releve des encaissements de l'annee."))
        scope.receipts.sortedBy { it.text("dueDate") }.forEach { row -> pdf.record(row.text("dueDate"), listOf(
            t("Bond reference", "Reference obligataire") to row.text("purchaseId"),
            t("Amount", "Montant") to cash(row.optDouble("amount"), row.text("currency").ifBlank { "RWF" }),
            t("Receipt flag", "Reception indiquee") to if (row.optBoolean("received")) t("Recorded as received", "Reception enregistree") else t("Not received", "Non recu"))) }
    }
    val archive = JsonRecord().put("format", "struct-capital-annual-report-v1").put("year", year)
        .put("periodStart", scope.start.toString()).put("periodEnd", scope.end.toString()).put("valuationDate", scope.cutoff.toString())
        .put("generatedOn", data.today.toString()).put("purchases", JsonList(scope.bonds)).put("holdings", JsonList(scope.shares))
        .put("closingBonds", JsonList(scope.closingBonds)).put("closingShares", JsonList(scope.closingShares))
        .put("uncertainPositions", JsonList(scope.uncertain)).put("prices", JsonList(priceArchive)).put("couponReceipts", JsonList(scope.receipts))
    return pdf.finish(archive.toString())
}
