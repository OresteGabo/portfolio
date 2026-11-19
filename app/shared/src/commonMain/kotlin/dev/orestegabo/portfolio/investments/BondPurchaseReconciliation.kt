package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

/** Personal allocation evidence for this issuance; an allocation can be smaller than the applied amount. */
internal fun importedBondPurchasesForPending(pending: JsonRecord, purchases: List<JsonRecord>): List<JsonRecord> =
    if (pending.text("status") != "submitted") emptyList() else purchases.filter { purchase ->
        purchase.text("id").isNotBlank() && purchase.text("id") != pending.text("id") &&
            purchase.text("status") in listOf("active", "matured", "sold") && purchase.text("confirmationDocumentId").isNotBlank() &&
            pending.text("issuer") == "Government of Rwanda" && purchase.text("issuer") == pending.text("issuer") &&
            pending.text("currency") == "RWF" && pending.text("market") == "primary" && purchase.text("market") == "primary" &&
            calendarDate(purchase.text("settlementDate")) != null && calendarDate(purchase.text("maturityDate")) != null &&
            pendingMatchesConfirmation(pending, purchase)
    }.sortedByDescending { it.text("purchaseDate") }

private fun bondLinkFieldsUnchanged(current: JsonRecord, expected: JsonRecord): Boolean {
    val textFields = listOf("instrumentType", "issuer", "currency", "market", "bondName", "isin", "status", "purchaseDate", "settlementDate",
        "maturityDate", "firstCouponDate", "confirmationDocumentId", "applicationFormDocumentId", "bidId")
    val amounts = listOf("faceValue", "pricePercent", "accruedInterestPaid", "feesPaid", "couponRate", "couponFrequency", "withholdingTaxRate", "tenorYears")
    return textFields.all { current.text(it) == expected.text(it) } && amounts.all { key ->
        current.has(key) == expected.has(key) && (!current.has(key) || current.optDouble(key).isFinite() && current.optDouble(key) == expected.optDouble(key))
    } && current.optJSONArray("couponDates")?.toString() == expected.optJSONArray("couponDates")?.toString()
}

/** One server transaction archives the estimate while preserving the confirmed purchase, its receipts and PDF. */
internal suspend fun linkImportedBondPurchase(api: PortfolioDataApi, pending: JsonRecord, selected: JsonRecord): JsonRecord {
    val purchases = api.request("/api/bonds/purchases").rows("purchases")
    val current = purchases.singleOrNull { it.text("id") == pending.text("id") && it.text("status") == "submitted" }
        ?: error("This pending bond is no longer available. Refresh your portfolio.")
    val purchase = importedBondPurchasesForPending(current, purchases).singleOrNull { it.text("id") == selected.text("id") }
        ?: error("This confirmation does not match the pending bond or applied amount.")
    require(bondLinkFieldsUnchanged(current, pending) && bondLinkFieldsUnchanged(purchase, selected)) {
        "These purchases changed. Refresh before linking."
    }
    return try {
        api.request("/api/bonds/purchases/${current.text("id")}/link-imported", "POST", JsonRecord()
            .put("confirmed", true).put("purchaseId", purchase.text("id"))
            .put("pending", JsonRecord(current.toString())).put("purchase", JsonRecord(purchase.toString())))
    } catch (failure: ApiException) {
        if (failure.status in listOf(404, 405)) error("Linking imported bonds is not available on this server yet.")
        throw failure
    }
}
