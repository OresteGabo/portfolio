package dev.orestegabo.portfolio.investments

/** Account purchase admission. Financial matching/save rules remain at their existing boundaries. */
internal fun validatePurchasePdfReview(review: JsonRecord, bondOnly: Boolean, expectedStatement: JsonRecord?) {
    val kind = review.text("kind")
    // Statements have their own review and do not confirm the purchase that opened the picker.
    if (kind == "statement") return
    val publicIssuance = kind == "bond-issuance"
    require(!bondOnly || kind == "bond" || publicIssuance) { "Use a BNR bond confirmation PDF." }
    val target = expectedStatement ?: return
    if (publicIssuance && target.text("kind") == "bond") return
    require(statementConfirmationMatches(target, review)) {
        "This PDF does not match the statement purchase. Choose its confirmation document."
    }
}

/** Guest imports support personal allocations/contracts, including incomplete fields for review. */
internal fun validateGuestPdfReview(review: JsonRecord) {
    require(review.text("kind") in listOf("bond", "equity")) { "unsupported" }
    require(review.optJSONObject("fields") != null) { "missing review fields" }
}
