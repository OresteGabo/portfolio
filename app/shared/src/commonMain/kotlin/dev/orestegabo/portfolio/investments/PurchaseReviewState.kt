package dev.orestegabo.portfolio.investments

/** Shared by the original review and conversation cards: one set of matching/validation rules. */
internal class PurchaseReviewState(review: JsonRecord, documents: List<JsonRecord>, purchases: List<JsonRecord>,
    historyComplete: Boolean, target: JsonRecord?, selection: String?) {
    val effectiveReview = purchaseReviewWithPublishedTerms(review, documents)
    val parsed = runCatching { importedPurchaseInput(effectiveReview) }
    val body = parsed.getOrNull()
    val bond = review.text("kind") == "bond"
    val matches = body?.let { input -> purchases.filter { pendingMatchesConfirmation(it, input) } }.orEmpty()
    val selectedId = selection ?: target?.text("id") ?: matches.singleOrNull()?.text("id")
    val selected = purchases.firstOrNull { it.text("id") == selectedId }
    val confirmed = body?.let { runCatching { completePendingPurchase(effectiveReview, selected) }.getOrNull() }
    val wrongTarget = target != null && (body == null || !bond || !pendingMatchesConfirmation(target, body))
    val ready = body != null && !wrongTarget && (!bond || historyComplete) &&
        (selected != null && pendingMatchesConfirmation(selected, body) || target == null && matches.isEmpty()) && confirmed != null
}
