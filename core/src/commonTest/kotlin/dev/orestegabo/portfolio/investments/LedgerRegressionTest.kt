package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Hand-calculated synthetic cases; independent of the implementation's calculation helpers. */
class LedgerRegressionTest {
    private val today = LocalDate(2026, 11, 15)
    private fun lot(id: String = "lot", shares: Int = 3, cost: Double = 10.0) = JsonRecord()
        .put("id", id).put("status", "active").put("ticker", "TEST").put("currency", "RWF")
        .put("tradeDate", "2026-01-01").put("shares", shares).put("netAmountPayable", cost)
    private fun sale(quantity: Double = 1.0) = JsonRecord().put("kind", "sale").put("holdingId", "lot")
        .put("date", today.toString()).put("currency", "RWF").put("shares", quantity)
        .put("price", 5.0).put("fees", .10).put("tax", .05)
    private fun cash(kind: String, currency: String, amount: Double, date: String = today.toString()) =
        JsonRecord().put("kind", kind).put("currency", currency).put("amount", amount).put("date", date)

    @Test fun completeDisposalConservesCostAndNetProceedsAcrossRoundedPartialSales() {
        val holding = lot()
        val before = holding.toString()
        val entries = mutableListOf<JsonRecord>()
        repeat(3) { entries += validateLedgerEntry(sale(), listOf(holding), entries, today) }
        assertEquals(listOf(3.33, 3.33, 3.34), entries.map { it.getDouble("allocatedCost") })
        assertEquals(listOf(1.52, 1.52, 1.51), entries.map { it.getDouble("realizedGain") })
        val balance = shareBalances(listOf(holding), entries).single()
        assertEquals(0.0, balance.getDouble("remainingShares"))
        assertEquals(0.0, balance.getDouble("remainingCost"))
        val cash = recordedCash(PortfolioSnapshot(holdings = listOf(holding)), emptyList(), entries, today).single()
        assertEquals(4.55, cash.balance) // 3 × (5 - .10 - .05) - original cost 10.
        assertEquals(4.55, cash.realizedGain)
        assertEquals(before, holding.toString())
        assertFailsWith<IllegalArgumentException> { validateLedgerEntry(sale(), listOf(holding), entries, today) }
    }

    @Test fun otherLotsAndDividendsNeverConsumeTheSelectedLotsSharesOrCost() {
        val unrelated = listOf(sale(999.0).put("holdingId", "other").put("allocatedCost", 9999),
            cash("dividend", "RWF", 1.0).put("holdingId", "lot").put("shares", 999).put("allocatedCost", 9999))
        val result = validateLedgerEntry(sale(3.0).put("allocatedCost", 9999).put("realizedGain", 9999)
            .put("ticker", "FORGED").put("amount", 9999), listOf(lot()), unrelated, today)
        assertEquals(10.0, result.getDouble("allocatedCost"))
        assertEquals(14.85, result.getDouble("amount"))
        assertEquals(4.85, result.getDouble("realizedGain"))
        assertEquals("TEST", result.text("ticker"))
    }

    @Test fun invalidSaleNumbersAndInactiveLotsNeverProduceTransactions() {
        val invalid = listOf(sale(.5), sale(-1.0), sale().put("price", 0), sale().put("price", 1e12 + 1),
            sale().put("fees", -.01), sale().put("tax", -.01), sale().put("shares", "NaN"),
            sale().put("price", "Infinity"), sale().put("fees", "NaN"))
        for (raw in invalid) assertFailsWith<IllegalArgumentException>(raw.toString()) {
            validateLedgerEntry(raw, listOf(lot()), emptyList(), today)
        }
        for (status in listOf("submitted", "sold", "cancelled")) {
            assertFailsWith<IllegalArgumentException>(status) {
                validateLedgerEntry(sale(), listOf(lot().put("status", status)), emptyList(), today)
            }
        }
    }

    @Test fun cashBalancesAreSeparatedByCurrencyAndStopAtTheInclusiveReportingDate() {
        val portfolio = PortfolioSnapshot(holdings = listOf(lot(cost = 100.0),
            lot("usd", cost = 10.0).put("currency", "USD"), lot("future", cost = 999.0).put("tradeDate", "2026-11-16")))
        val entries = listOf(cash("deposit", "RWF", 200.0), cash("withdrawal", "RWF", 20.0),
            cash("dividend", "RWF", 3.25), cash("sale", "RWF", 40.0).put("realizedGain", -2.50),
            cash("deposit", "USD", 30.0), cash("withdrawal", "USD", 1.25),
            cash("dividend", "RWF", 999.0, "2026-11-16"))
        val result = recordedCash(portfolio, emptyList(), entries, today)
        assertEquals(listOf(RecordedCash("RWF", 123.25, 3.25, -2.5), RecordedCash("USD", 18.75, 0.0, 0.0)), result)
    }

    @Test fun cashUsesPaidBondCostAndOnlyDueManuallyConfirmedCouponReceipts() {
        fun bond(status: String = "active", date: String = today.toString()) = JsonRecord().put("status", status)
            .put("purchaseDate", date).put("currency", "RWF").put("faceValue", 1000)
            .put("pricePercent", 98).put("feesPaid", 2).put("accruedInterestPaid", 3)
        fun receipt(received: Boolean = true, method: String = "manual", due: String = today.toString()) =
            JsonRecord().put("received", received).put("method", method).put("dueDate", due).put("currency", "RWF").put("amount", 10)
        val result = recordedCash(PortfolioSnapshot(purchases = listOf(bond(), bond("submitted"), bond(date = "2026-11-16"))),
            listOf(receipt(), receipt(false), receipt(method = "automatic"), receipt(due = "2026-11-16")),
            listOf(cash("deposit", "RWF", 1000.0)), today).single()
        assertEquals(25.0, result.balance) // 1,000 - (980 + 2 + 3) + 10.
        assertEquals(0.0, result.dividends)
    }

    @Test fun genericCashValidationNormalizesNotesAndRejectsInvalidBoundariesWithoutMutation() {
        val raw = cash("deposit", "RWF", 1e15).put("note", "  deposit  ").put("realizedGain", 999)
        val before = raw.toString()
        val saved = validateLedgerEntry(raw, emptyList(), emptyList(), today)
        assertEquals("deposit", saved.text("note")); assertFalse(saved.has("realizedGain"))
        assertEquals(before, raw.toString())
        for (invalid in listOf(rawCopy(raw).put("amount", 0), rawCopy(raw).put("amount", 1e15 + 1),
            rawCopy(raw).put("currency", "rwf"), rawCopy(raw).put("date", "2026-02-30"),
            rawCopy(raw).put("kind", "transfer"), rawCopy(raw).put("note", "x".repeat(501)))) {
            assertFailsWith<IllegalArgumentException> { validateLedgerEntry(invalid, emptyList(), emptyList(), today) }
        }
        assertEquals(500, validateLedgerEntry(rawCopy(raw).put("note", "x".repeat(500)), emptyList(), emptyList(), today).text("note").length)
    }
    private fun rawCopy(record: JsonRecord) = JsonRecord(record.toString())
}
