package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.PortfolioDataApi

/** Existing contract-backed purchases only; dashboard aggregate rows can never be confirmation evidence. */
internal fun importedSharePurchasesForOrder(order: JsonRecord, holdings: List<JsonRecord>): List<JsonRecord> =
    holdings.filter { purchase ->
        purchase.text("id").isNotBlank() && !purchase.text("id").startsWith("aggregate:") &&
            purchase.text("contractDocumentId").isNotBlank() && purchase.text("contractNote").isNotBlank() &&
            purchase.text("status") in listOf("active", "sold") &&
            calendarDate(purchase.text("tradeDate")) != null && calendarDate(purchase.text("settlementDate")) != null &&
            runCatching { pendingShareContractInput(JsonRecord().put("kind", "equity")
                .put("fields", purchase).put("missingFields", JsonList()), order) }.isSuccess
    }.sortedByDescending { it.text("tradeDate") }

/** Reuses the existing atomic confirmation API. Never uploads a PDF, creates an independent holding or cancels an order. */
internal suspend fun linkImportedSharePurchase(api: PortfolioDataApi, order: JsonRecord, selected: JsonRecord): JsonRecord {
    val currentOrder = api.request("/api/bonds/share-orders").rows("orders").singleOrNull { it.text("id") == order.text("id") }
        ?: error("This order is no longer pending. Refresh your portfolio.")
    require(currentOrder.optInt("version") == order.optInt("version")) { "This order changed. Refresh before continuing." }
    val holdings = api.request("/api/bonds/equities").rows("holdings")
    val purchase = importedSharePurchasesForOrder(currentOrder, holdings).singleOrNull { it.text("id") == selected.text("id") }
        ?: error("This purchase no longer matches the pending order. Refresh before continuing.")
    val textFields = listOf("isin", "ticker", "currency", "securityName", "broker", "contractNote", "tradeDate", "settlementDate", "contractDocumentId")
    val amountFields = listOf("shares", "pricePerShare", "grossConsideration", "brokerageCommission", "rseTransactionFee", "csdTransactionLevy", "cmaTransactionFee", "totalCharges", "netAmountPayable")
    require(textFields.all { purchase.text(it) == selected.text(it) } && amountFields.all { key ->
        purchase.has(key) == selected.has(key) && (!purchase.has(key) ||
            purchase.optDouble(key).isFinite() && purchase.optDouble(key) == selected.optDouble(key))
    }) {
        "This purchase changed. Review its contract again before linking."
    }
    // The server also rejects ambiguous financial identities. Block obvious duplicate contract records before a write.
    require(holdings.count { it.text("contractNote") == purchase.text("contractNote") &&
        it.text("broker").equals(purchase.text("broker"), true) } == 1) {
        "More than one purchase uses this contract. Review the duplicates before linking."
    }
    return api.request("/api/bonds/share-orders/${currentOrder.text("id")}/confirm", "POST",
        JsonRecord().put("confirmed", true).put("version", currentOrder.optInt("version"))
            .put("purchase", JsonRecord(purchase.toString())))
}
