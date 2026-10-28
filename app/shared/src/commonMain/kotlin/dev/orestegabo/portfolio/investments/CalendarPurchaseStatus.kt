package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

enum class CalendarPurchaseStatus(val label: String) {
    UNAVAILABLE("Purchase status unavailable"), NONE(""), PENDING("Application recorded"),
    CONFIRMED("Purchase confirmed"), CONFIRMED_WITH_PDF("Purchase confirmed · PDF saved"),
    MIXED("Confirmed purchase · Pending application"), RECORDED("Purchase recorded")
}

data class CalendarIssuePurchases(val status: CalendarPurchaseStatus, val records: List<JsonRecord>)

/** A shared issuance report is not a personal purchase. Match the actual auction and security. */
fun calendarIssuePurchases(issue: JsonRecord, purchases: List<JsonRecord>, documents: List<JsonRecord>, historyComplete: Boolean): CalendarIssuePurchases {
    fun security(value: String) = value.uppercase().filterNot(Char::isWhitespace).removeSuffix("/MN")
    val settlement = calendarDate(issue.text("settlementDate"))
    val maturity = calendarDate(issue.text("maturityDate"))
    val isin = security(issue.text("isin"))
    val currency = issue.text("currency").ifBlank { "RWF" }.uppercase()
    val records = purchases.filter { purchase ->
        val purchaseIsin = security(purchase.text("isin"))
        val purchaseCurrency = purchase.text("currency").uppercase()
        val type = purchase.text("instrumentType").lowercase()
        purchase.text("status") != "cancelled" && (purchaseCurrency.isBlank() || purchaseCurrency == currency) &&
            (type.isBlank() || type in setOf("treasury", "government")) && settlement != null && calendarDate(purchase.text("settlementDate")) == settlement &&
            if (isin.isNotBlank() && purchaseIsin.isNotBlank()) isin == purchaseIsin
            else maturity != null && calendarDate(purchase.text("maturityDate")) == maturity
    }
    val active = records.filter { it.text("status") == "active" }
    val pending = records.any { it.text("status") == "submitted" }
    val documentIds = documents.map { it.text("id") }.filter(String::isNotBlank).toSet()
    val withPdf = active.any { it.text("confirmationDocumentId").takeIf(String::isNotBlank) in documentIds }
    return CalendarIssuePurchases(when {
        active.isNotEmpty() && pending -> CalendarPurchaseStatus.MIXED
        withPdf -> CalendarPurchaseStatus.CONFIRMED_WITH_PDF
        active.isNotEmpty() -> CalendarPurchaseStatus.CONFIRMED
        pending -> CalendarPurchaseStatus.PENDING
        records.isNotEmpty() -> CalendarPurchaseStatus.RECORDED
        !historyComplete -> CalendarPurchaseStatus.UNAVAILABLE
        else -> CalendarPurchaseStatus.NONE
    }, records)
}

fun calendarIssueGuidance(issue: JsonRecord, purchases: CalendarIssuePurchases, today: LocalDate): String = when (purchases.status) {
    CalendarPurchaseStatus.CONFIRMED_WITH_PDF -> "Your purchase is confirmed and its confirmation PDF is saved. No new bid is needed for this purchase."
    CalendarPurchaseStatus.CONFIRMED -> "Your purchase is already confirmed. No new bid is needed for this purchase."
    CalendarPurchaseStatus.PENDING -> "Your application is already recorded and is awaiting allocation confirmation. Do not submit the same application again."
    CalendarPurchaseStatus.MIXED -> "You have a confirmed purchase and another application awaiting confirmation for this bond. Review the pending application; do not submit the confirmed purchase again."
    CalendarPurchaseStatus.RECORDED -> "A purchase for this bond is already recorded. Check its status in your portfolio before making another application."
    CalendarPurchaseStatus.UNAVAILABLE -> "Your purchase history has not been checked yet. Refresh it before deciding whether another application is needed."
    CalendarPurchaseStatus.NONE -> {
        val open = calendarDate(issue.text("openBookDate"))
        val close = calendarDate(issue.text("closingBookDate"))
        when {
            close != null && today > close -> "The subscription period has ended. Review the published results and any application you submitted."
            open != null && today < open -> "Review the prospectus and prepare your bid before the book opens."
            open != null && close != null && today >= open && today == close -> "If you are applying, confirm your bid with your broker before today’s closing deadline."
            open != null && close != null && today >= open && today < close -> "If you want to apply, review the terms and submit your bid to your broker before the book closes."
            else -> "Review the published subscription dates and bond terms."
        }
    }
}
