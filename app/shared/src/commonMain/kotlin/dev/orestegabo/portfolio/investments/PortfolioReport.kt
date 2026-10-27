package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.math.floor
import kotlin.math.abs

internal data class PortfolioReportData(
    val snapshot: PortfolioSnapshot,
    val quotes: JsonRecord?,
    val statementItems: List<JsonRecord>,
    val email: String,
    val monthlyTarget: Long,
    val today: LocalDate,
    val language: String,
    val couponReceipts: List<JsonRecord>? = null,
)

/** Uses the same valuation and contribution rules as the dashboard; never a truncated preview. */
internal fun generatePortfolioReport(data: PortfolioReportData): ByteArray {
    require(data.snapshot.recentPurchaseIds == null && data.snapshot.recentHoldingIds == null)
    require(validInvestmentTarget(data.monthlyTarget))
    val french = data.language == "fr"
    fun t(en: String, fr: String) = if (french) fr else en
    fun number(value: Double): String {
        if (!value.isFinite()) return t("Unavailable", "Indisponible")
        val whole = abs(floor(value)).toLong().toString().reversed().chunked(3).joinToString(if (french) " " else ",").reversed()
        return (if (value < 0) "-" else "") + whole
    }
    fun cash(value: Double, currency: String = "RWF"): String {
        if (!value.isFinite() || currency == "RWF") return "$currency ${number(value)}"
        val cents = kotlin.math.round(abs(value) * 100).toLong()
        val whole = (cents / 100).toString().reversed().chunked(3).joinToString(if (french) " " else ",").reversed()
        return "$currency " + (if (value < 0) "-" else "") + whole + (if (french) "," else ".") + (cents % 100).toString().padStart(2, '0')
    }
    fun rate(value: Double?) = value?.takeIf { it.isFinite() }?.let { ((it * 1000).toLong() / 10.0).toString().let { value -> if (french) value.replace('.', ',') + " %" else "$value%" } } ?: t("Unavailable", "Indisponible")
    fun status(value: String) = if (!french) value else when (value) {
        "active" -> "Actif"; "submitted" -> "En attente"; "sold" -> "Vendu"; "matured" -> "Échu"; "cancelled" -> "Annulé"; else -> value
    }
    val pdf = ReportPdf(t("STRUCT CAPITAL / PORTFOLIO REPORT", "STRUCT CAPITAL / RAPPORT DE PORTEFEUILLE"),
        t("Private financial report - All years", "Rapport financier privé - Toutes les années"), french = french)
    val snapshot = data.snapshot
    pdf.heading(t("Your investment record", "Vos investissements"), large = true)
    pdf.paragraph("${data.email}  |  ${data.today}")
    pdf.paragraph(t("All saved bond and share purchases, current holdings and target progress. Amounts are grouped by currency; currencies are never added together.",
        "Tous les achats d'obligations et d'actions enregistrés, les positions actuelles et la progression des objectifs. Les devises sont présentées séparément."))
    val valuations = snapshot.valuation(data.today, data.quotes)
    if (valuations.isEmpty()) pdf.paragraph(t("No investments recorded yet.", "Aucun investissement enregistré."))
    valuations.forEach { value ->
        val cost = value.cost
        pdf.ensure(410.0)
        pdf.banner(t("Estimated investment worth", "Patrimoine investi estimé"), cash(value.estimatedNetWorth, cost.currency))
        val gains = value.shares.mapNotNull { it.gainLoss }
        pdf.summaryMetrics(listOf(
            ReportMetric(t("Bond principal", "Capital obligataire"), cash(cost.bondFaceValue, cost.currency)),
            ReportMetric(t("RSE equity value", "Valeur des actions RSE"), cash(value.shareValue, cost.currency)),
            ReportMetric(t("Total cash cost of active holdings", "Coût total des positions actives"), cash(cost.investedCash, cost.currency)),
            ReportMetric(t("Saved annual net coupon", "Coupon net annuel enregistré"), cash(cost.netAnnualCoupon, cost.currency)),
            ReportMetric(t("Unrealized share gain / loss (priced holdings)", "Gain / perte latent(e) des actions cotées"),
                if (gains.isEmpty()) t("Unavailable", "Indisponible") else cash(gains.sum(), cost.currency), gains.takeIf { it.isNotEmpty() }?.sum()),
        ))
        pdf.allocation(t("Asset allocation", "Répartition des actifs"), t("Bonds", "Obligations"), t("Shares", "Actions"), cost.bondFaceValue, value.shareValue)
        if (value.unpricedShares > 0) pdf.paragraph(t("${value.unpricedShares} unpriced position(s) use purchase cash cost; their gains are unavailable.",
            "${value.unpricedShares} position(s) sans cours sont valorisées au coût d'achat ; leurs gains sont indisponibles."))
        pdf.gap()
    }
    val quoteDate = data.quotes?.optJSONObject("source")?.text("publishedAt").orEmpty().take(10)
    pdf.paragraph(t("Quote date: ${quoteDate.ifBlank { "unavailable" }}. These are unrealized changes since purchase, after recorded buying costs. Sale proceeds, realized gains, dividends, cash balances, liabilities and future selling fees are not calculated. Coupons are annual estimates, not income received. Bond principal is not a market valuation.",
        "Date des cours : ${quoteDate.ifBlank { "indisponible" }}. Les gains latents incluent les frais d'achat enregistrés. Produits de vente, gains réalisés, dividendes, liquidités, dettes et frais de vente futurs ne sont pas calculés. Les coupons sont des estimations annuelles, pas des revenus reçus. Le capital obligataire n'est pas une valeur de marché."))
    if (data.quotes?.optJSONObject("source")?.let { it.optBoolean("stale") || it.text("status") == "error" } == true)
        pdf.paragraph(t("Available prices may be stale. Check the quote date before relying on this estimate.", "Les cours disponibles peuvent être anciens. Vérifiez leur date avant de vous fier à cette estimation."))
    data.couponReceipts?.let { receipts ->
        pdf.newPage()
        pdf.heading(t("Coupon receipts", "Réceptions des coupons"), large = true)
        pdf.paragraph(t("Receipts reflect manual confirmations or automatic marking, not bank verification. Due dates use Rwanda time.",
            "Les réceptions sont confirmées manuellement ou marquées automatiquement, sans vérification bancaire. Les échéances suivent l'heure du Rwanda."))
        if(receipts.isEmpty())pdf.paragraph(t("No coupon receipts recorded.", "Aucune réception de coupon enregistrée."))
        else pdf.table(listOf(t("Due date", "Échéance"),t("Bond", "Obligation"),t("Net amount", "Montant net"),t("Status", "Statut")),
            receipts.map { receipt -> listOf(receipt.text("dueDate"),snapshot.purchases.firstOrNull { it.text("id")==receipt.text("purchaseId") }?.text("bondName") ?: receipt.text("purchaseId"),
                cash(receipt.optDouble("amount"),receipt.text("currency").ifBlank { "RWF" }),if(!receipt.optBoolean("received"))t("Not received","Non reçu") else if(receipt.text("method")=="automatic")t("Automatic","Automatique") else t("Confirmed","Confirmé")) },
            listOf(95.0,160.0,145.0,123.0))
    }
    pdf.newPage()
    pdf.heading(t("Shares: gains and losses", "Actions : gains et pertes"), large = true)
    val positions = snapshot.valuedShares(data.today, data.quotes).sortedWith(compareBy<ValuedSharePosition> { it.position.currency }.thenByDescending { it.gainLoss ?: -Double.MAX_VALUE })
    if (positions.isEmpty()) pdf.paragraph(t("No active shares.", "Aucune action active."))
    positions.forEach { value ->
        val p = value.position
        val name = snapshot.holdings.firstOrNull { it.text("ticker") == p.ticker }?.text("securityName").orEmpty()
        pdf.record(listOf(p.ticker, name).filter { it.isNotBlank() }.distinct().joinToString(" / "), listOf(
            t("Shares", "Actions") to number(p.shares),
            t("Purchase cash cost", "Coût d'achat") to cash(p.cashCost, p.currency),
            t("Closing price", "Cours de clôture") to (value.closingPrice?.let { cash(it, p.currency) } ?: t("Unavailable", "Indisponible")),
            t("Estimated value", "Valeur estimée") to cash(value.estimatedValue, p.currency),
            t("Unrealized gain / loss", "Gain / perte latent(e)") to (value.gainLoss?.let { cash(it, p.currency) + " / " + rate(value.gainLossRate) } ?: t("Unavailable", "Indisponible"))), highlightLast = value.gainLoss)
    }
    pdf.newPage()
    pdf.heading(t("Investment targets", "Objectifs d'investissement"), large = true)
    pdf.paragraph(t("Reference monthly target: ${cash(data.monthlyTarget.toDouble())}. Annual reference: ${cash(data.monthlyTarget * 12.0)}. The current device setting is used for every year; historical target changes are not stored. Progress counts confirmed RWF purchases including recorded fees, not market gains. Pending amounts are separate.",
        "Objectif mensuel de référence : ${cash(data.monthlyTarget.toDouble())}. Référence annuelle : ${cash(data.monthlyTarget * 12.0)}. Le réglage actuel de cet appareil est appliqué à chaque année ; les anciens objectifs ne sont pas conservés. La progression compte les achats RWF confirmés et les frais enregistrés, pas les gains de marché. Les montants en attente restent séparés."))
    val years = reportYears(data)
    val annualTotals = years.associateWith { year -> (1..12).map { month ->
        requireNotNull(monthlyInvestmentProgress(snapshot.copy(monthlyInvestmentTotals = null), LocalDate(year, month, 1), data.monthlyTarget, data.today))
    } }
    annualTotals.forEach { (year, rows) ->
        val total = rows.sumOf { it.confirmed }
        pdf.bar(year.toString(), total, maxOf(data.monthlyTarget * 12.0, total), "${cash(total)} / ${cash(data.monthlyTarget * 12.0)}")
    }
    val months = if (french) listOf("Jan", "Fév", "Mars", "Avr", "Mai", "Juin", "Juil", "Août", "Sept", "Oct", "Nov", "Déc")
        else listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    annualTotals.forEach { (year, rows) ->
        pdf.ensure(485.0)
        pdf.heading(year.toString())
        pdf.metric(t("Confirmed / annual reference", "Confirmé / référence annuelle"), "${cash(rows.sumOf { it.confirmed })} / ${cash(data.monthlyTarget * 12.0)}")
        pdf.table(listOf(t("Month", "Mois"), t("Confirmed RWF", "Confirmé RWF"), t("Pending RWF", "En attente RWF"), t("Target RWF", "Objectif RWF"), t("Progress", "Progression")), rows.mapIndexed { index, row ->
            val future = LocalDate(year, index + 1, 1) > data.today
            listOf(months[index], if (future) "-" else number(row.confirmed), if (future) "-" else number(row.pending), number(row.target.toDouble()), if (future) "-" else rate(row.confirmed / row.target))
        }, listOf(64.0, 124.0, 124.0, 124.0, 87.0))
    }
    pdf.newPage()
    pdf.heading(t("All bond purchases", "Tous les achats d'obligations"), true)
    pdf.paragraph(t("${snapshot.purchases.size} record(s). Pending and inactive records are retained here but excluded from active portfolio worth.",
        "${snapshot.purchases.size} entrée(s). Les achats en attente et inactifs sont conservés ici, mais exclus du patrimoine actif."))
    snapshot.purchases.sortedByDescending { it.text("purchaseDate") }.forEach { bond ->
        val c = bond.text("currency")
        pdf.record(bond.text("bondName").ifBlank { bond.text("id") }, listOf(
            t("Status / purchase date", "Statut / date d'achat") to "${status(bond.text("status"))} / ${bond.text("purchaseDate")}",
            "ISIN" to bond.text("isin"), t("Issuer", "Émetteur") to bond.text("issuer"),
            t("Principal / cash cost", "Capital / coût total") to "${cash(bond.optDouble("faceValue", 0.0), c)} / ${cash(bond.bondCashCost(), c)}",
            t("Coupon / withholding tax", "Coupon / retenue fiscale") to "${rate(bond.optDouble("couponRate", 0.0))} / ${rate(bond.optDouble("withholdingTaxRate", 0.0))}",
            t("Annual net coupon (contract)", "Coupon net annuel (contrat)") to cash(bond.netAnnualBondCoupon(), c),
            t("Settlement / maturity", "Règlement / échéance") to "${bond.text("settlementDate")} / ${bond.text("maturityDate")}",
            t("Broker", "Courtier") to bond.text("broker"), t("Record ID", "Identifiant") to bond.text("id")))
    }
    pdf.newPage()
    pdf.heading(t("All share purchases", "Tous les achats d'actions"), true)
    pdf.paragraph(t("${snapshot.holdings.size} record(s).", "${snapshot.holdings.size} entrée(s)."))
    snapshot.holdings.sortedByDescending { it.text("tradeDate") }.forEach { share ->
        val c = share.text("currency")
        pdf.record("${share.text("ticker")} / ${share.text("securityName")}", listOf(
            t("Status / trade date", "Statut / date d'achat") to "${status(share.text("status"))} / ${share.text("tradeDate")}",
            "ISIN" to share.text("isin"), t("Shares", "Actions") to number(share.optDouble("shares", 0.0)),
            t("Purchase price per share", "Prix d'achat unitaire") to cash(share.optDouble("pricePerShare", 0.0), c),
            t("Total cash cost", "Coût total décaissé") to cash(share.optDouble("netAmountPayable", 0.0), c),
            t("Contract / broker", "Contrat / courtier") to "${share.text("contractNote")} / ${share.text("broker")}",
            t("Record ID", "Identifiant") to share.text("id")))
    }
    pdf.newPage()
    pdf.heading(t("Documents and missing records", "Documents et entrées à compléter"), true)
    pdf.paragraph(t("Original PDFs are not included in this report. Download them separately from Documents before deleting your account. A structured portfolio-data.json attachment inside this PDF preserves saved investment fields and document metadata. This is an archive, not an automatic restore file.",
        "Les PDF originaux ne sont pas inclus dans ce rapport. Téléchargez-les séparément depuis Documents avant de supprimer votre compte. Une pièce jointe portfolio-data.json dans ce PDF conserve les champs enregistrés et les métadonnées des documents. Il s'agit d'une archive, pas d'un fichier de restauration automatique."))
    snapshot.documents.forEach { document -> pdf.record(document.text("label").ifBlank { document.text("originalFileName").ifBlank { document.text("id") } }, listOf(
        t("Document ID", "Identifiant du document") to document.text("id"), t("Created", "Créé le") to document.text("createdAt"))) }
    if (snapshot.documents.isEmpty()) pdf.paragraph(t("No saved documents.", "Aucun document enregistré."))
    data.statementItems.forEach { row -> pdf.record(row.text("title").ifBlank { t("Missing purchase contract", "Contrat d'achat manquant") }, listOf(
        t("Date", "Date") to row.text("date"), t("Amount", "Montant") to cash(row.optDouble("amount")),
        t("Contract", "Contrat") to row.text("contractNote"), t("Record ID", "Identifiant") to row.text("id"))) }
    val archive = JsonRecord().put("format", "gabo-portfolio-report-v1").put("generatedOn", data.today.toString())
        .put("couponReceipts", data.couponReceipts?.let(::JsonList)).put("email", data.email).put("monthlyTargetRwf", data.monthlyTarget).put("targetBasis", "current-setting-not-historical")
        .put("purchases", JsonList(snapshot.purchases)).put("holdings", JsonList(snapshot.holdings))
        .put("documents", JsonList(snapshot.documents)).put("missingStatementItems", JsonList(data.statementItems))
    return pdf.finish(archive.toString())
}

internal fun reportYears(data: PortfolioReportData): List<Int> =
    (data.snapshot.purchases.mapNotNull { calendarDate(it.text("purchaseDate"))?.year } +
        data.snapshot.holdings.mapNotNull { calendarDate(it.text("tradeDate"))?.year } + data.today.year)
        .filter { it in 1900..data.today.year }.distinct().sortedDescending()
