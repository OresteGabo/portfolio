package dev.orestegabo.portfolio.investments

import kotlin.test.*

class PdfReviewPolicyTest {
    @Test fun purchaseContextPreservesAllDocumentKindsAndUnknownKindFallback() {
        for (kind in listOf("bond", "equity", "statement", "bond-issuance", "unknown", "")) {
            val review = JsonRecord().put("kind", kind).readOnlyCopy()
            validatePurchasePdfReview(review, false, null)
            if (kind in listOf("bond", "statement", "bond-issuance")) validatePurchasePdfReview(review, true, null)
            else assertEquals("Use a BNR bond confirmation PDF.", assertFailsWith<IllegalArgumentException> {
                validatePurchasePdfReview(review, true, null)
            }.message)
        }
    }

    @Test fun statementReviewBypassesPurchaseTargetWhilePublicIssuanceRequiresABondTarget() {
        for (targetKind in listOf("bond", "equity", "unknown")) {
            val target = JsonRecord().put("kind", targetKind)
            validatePurchasePdfReview(JsonRecord().put("kind", "statement"), true, target)
            val issuance = JsonRecord().put("kind", "bond-issuance")
            if (targetKind == "bond") validatePurchasePdfReview(issuance, true, target)
            else assertEquals("This PDF does not match the statement purchase. Choose its confirmation document.",
                assertFailsWith<IllegalArgumentException> { validatePurchasePdfReview(issuance, true, target) }.message)
        }
    }

    @Test fun purchaseTargetStillChecksTheExistingContractFinancialAndIdentityRules() {
        val fields = JsonRecord().put("currency", "RWF").put("broker", "BK Capital").put("contractNote", "note")
            .put("ticker", "BOK").put("tradeDate", "2026-11-01").put("shares", 100)
            .put("pricePerShare", 720).put("netAmountPayable", 73231.2)
        val review = JsonRecord().put("kind", "equity").put("fields", fields).put("missingFields", JsonList()).readOnlyCopy()
        val target = JsonRecord().put("kind", "equity").put("contractNote", "note").put("ticker", "BOK")
            .put("date", "2026-11-01").put("shares", 100).put("pricePerShare", 720).put("amount", 73231.2)
        val original = review.toString()
        validatePurchasePdfReview(review, false, target)
        for ((key, value) in listOf("ticker" to "OTHER", "contractNote" to "different", "date" to "2026-11-02")) {
            assertFailsWith<IllegalArgumentException> {
                validatePurchasePdfReview(review, false, JsonRecord(target.toString()).put(key, value))
            }
        }
        for (key in listOf("shares", "pricePerShare", "amount")) assertFailsWith<IllegalArgumentException> {
            validatePurchasePdfReview(review, false, JsonRecord(target.toString()).put(key, 1))
        }
        assertEquals(original, review.toString())
    }

    @Test fun guestAdmissionAcceptsIncompletePersonalReviewsWithoutAuthorizingASave() {
        for (kind in listOf("bond", "equity")) {
            val review = JsonRecord().put("kind", kind).put("fields", JsonRecord())
                .put("missingFields", JsonList(listOf("amount"))).readOnlyCopy()
            validateGuestPdfReview(review)
            assertFailsWith<IllegalArgumentException> { importedPurchaseInput(review) }
        }
    }

    @Test fun guestAdmissionRejectsOtherKindsAndMissingFieldsInTheSameOrder() {
        for (kind in listOf("statement", "bond-issuance", "unknown", "")) {
            assertEquals("unsupported", assertFailsWith<IllegalArgumentException> {
                validateGuestPdfReview(JsonRecord().put("kind", kind))
            }.message)
        }
        for (kind in listOf("bond", "equity")) {
            assertEquals("missing review fields", assertFailsWith<IllegalArgumentException> {
                validateGuestPdfReview(JsonRecord().put("kind", kind))
            }.message)
        }
    }
}
