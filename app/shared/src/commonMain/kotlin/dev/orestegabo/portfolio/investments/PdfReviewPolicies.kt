package dev.orestegabo.portfolio.investments

/** Account purchase admission. Financial matching/save rules remain at their existing boundaries. */
internal fun validatePurchasePdfReview(review: JsonRecord, bondOnly: Boolean, expectedStatement: JsonRecord?) {
    val publicIssuance = review.text("kind") == "bond-issuance"
    val statement = review.text("kind") == "statement"
    // A multi-transaction statement has its own review; it must not be
    // rejected or used as confirmation for the purchase that opened the picker.
    require(statement || !bondOnly || review.text("kind") == "bond" || publicIssuance) { "Use a BNR bond confirmation PDF." }
    require(statement || expectedStatement == null || publicIssuance && expectedStatement.text("kind") == "bond" || statementConfirmationMatches(expectedStatement, review)) {
        "This PDF does not match the statement purchase. Choose its confirmation document."
    }
}

/** Guest imports support personal allocations/contracts, including incomplete fields for review. */
internal fun validateGuestPdfReview(review: JsonRecord) {
    require(review.text("kind") in listOf("bond", "equity")) { "unsupported" }
    require(review.optJSONObject("fields") != null) { "missing review fields" }
}
