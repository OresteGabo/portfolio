package dev.orestegabo.portfolio.investments

/** Suggestions only: the user must choose an order; its actual contract date and costs remain authoritative. */
internal fun matchingPendingShareOrders(review: JsonRecord, orders: List<JsonRecord>): List<JsonRecord> {
    if (review.text("kind") != "equity") return emptyList()
    val purchase = runCatching { importedPurchaseInput(review) }.getOrNull() ?: return emptyList()
    if (listOf("shares", "pricePerShare").any { !purchase.optDouble(it).isFinite() || purchase.optDouble(it) <= 0 }) return emptyList()
    val tradeDate = calendarDate(purchase.text("tradeDate")) ?: return emptyList()
    return orders.filter { order ->
        val requestDate = calendarDate(order.text("requestDate"))
        order.text("id").isNotBlank() && order.text("status") == "pending" &&
            order.text("ticker").trim().equals(purchase.text("ticker").trim(), true) &&
            order.text("currency") == purchase.text("currency") &&
            order.optDouble("shares") == purchase.optDouble("shares") &&
            order.optDouble("pricePerShare") == purchase.optDouble("pricePerShare") &&
            requestDate != null && requestDate.year == tradeDate.year && requestDate.month == tradeDate.month
    }
}
