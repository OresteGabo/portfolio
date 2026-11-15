package dev.orestegabo.portfolio.investments

import kotlin.random.Random
import kotlin.test.*

class ShareBalancesCharacterizationTest {
    @Test fun groupingKeepsBlankDuplicateAndUnknownIdsAndUnrelatedFieldsExactly() {
        val holdings = listOf(
            JsonRecord("{\"id\":\"a\",\"shares\":100,\"netAmountPayable\":100.005,\"raw\":0.100000000000000001}"),
            JsonRecord().put("id", "").put("shares", 0).put("netAmountPayable", 99),
            JsonRecord().put("id", "a").put("shares", 20).put("netAmountPayable", .02),
            JsonRecord().put("id", "A").put("shares", 10).put("netAmountPayable", 20))
        val entries = listOf(
            JsonRecord().put("kind", "sale").put("holdingId", "a").put("shares", 5).put("allocatedCost", .005),
            JsonRecord().put("kind", "deposit").put("holdingId", "a").put("shares", 999),
            JsonRecord().put("kind", "sale").put("shares", 10).put("allocatedCost", 50),
            JsonRecord().put("kind", "sale").put("holdingId", "absent").put("shares", 10).put("allocatedCost", 10),
            JsonRecord().put("kind", "sale").put("holdingId", "a").put("shares", 30).put("allocatedCost", .005))
        val before = (holdings + entries).map(JsonRecord::toString)
        assertEquals(referenceShareBalances(holdings, entries).map(JsonRecord::toString), shareBalances(holdings, entries).map(JsonRecord::toString))
        assertEquals(before, (holdings + entries).map(JsonRecord::toString))
    }

    @Test fun seededMixedHistoriesMatchTheExistingDecimalAndQuantitySemantics() {
        val random = Random(20261115)
        repeat(40) {
            val holdings = (0 until 12).map { index ->
                JsonRecord().put("id", "lot-$index").put("shares", random.nextInt(0, 1000))
                    .put("netAmountPayable", random.nextInt(0, 1_000_000) / 1000.0)
                    .put("currency", if (index % 2 == 0) "RWF" else "USD")
                    .put("status", if (index % 3 == 0) "sold" else "active")
            }
            val entries = (0 until 100).map { index ->
                JsonRecord().put("kind", if (index % 4 == 0) "dividend" else "sale")
                    .put("holdingId", "lot-${random.nextInt(0, 16)}").put("shares", random.nextInt(0, 30))
                    .put("allocatedCost", random.nextInt(0, 50_000) / 1000.0).put("date", "2030-01-01")
            }
            assertEquals(referenceShareBalances(holdings, entries).map(JsonRecord::toString), shareBalances(holdings, entries).map(JsonRecord::toString))
        }
    }

    @Test fun eachInvocationReflectsUpdatedInputsWithoutRetainedFinancialState() {
        val lot = JsonRecord().put("id", "one").put("shares", 100).put("netAmountPayable", 1000)
        val sales = mutableListOf<JsonRecord>()
        assertEquals(1000.0, shareBalances(listOf(lot), sales).single().getDouble("remainingCost"))
        sales += JsonRecord().put("kind", "sale").put("holdingId", "one").put("shares", 10).put("allocatedCost", 100)
        assertEquals(900.0, shareBalances(listOf(lot), sales).single().getDouble("remainingCost"))
        sales.single().put("allocatedCost", 200)
        lot.put("netAmountPayable", 2000)
        assertEquals(1800.0, shareBalances(listOf(lot), sales).single().getDouble("remainingCost"))
    }

    @Test fun malformedMoneyAndNonFiniteQuantitiesRemainRejected() {
        for (field in listOf("shares", "netAmountPayable")) {
            val lot = JsonRecord().put("id", "one").put("shares", 10).put("netAmountPayable", 20).put(field, "NaN")
            assertFailsWith<IllegalArgumentException> { referenceShareBalances(listOf(lot), emptyList()) }
            assertFailsWith<IllegalArgumentException> { shareBalances(listOf(lot), emptyList()) }
        }
    }
    private class CountedEntries(private val rows: List<JsonRecord>) : AbstractList<JsonRecord>() {
        var reads = 0
            private set
        override val size: Int get() = rows.size
        override fun get(index: Int): JsonRecord = rows[index].also { reads++ }
    }

    @Test fun ledgerTraversalIsLinearRatherThanRepeatedForEveryHolding() {
        val fixture = shareBalanceFixture(200, 500)
        val entries = CountedEntries(fixture.entries)
        val result = shareBalances(fixture.holdings, entries)
        assertEquals(fixture.holdings.size, result.size)
        assertEquals(fixture.entries.size, entries.reads)
        assertEquals(referenceShareBalances(fixture.holdings, fixture.entries).map(JsonRecord::toString), result.map(JsonRecord::toString))
    }

    @Test fun emptyHoldingsNeedNoLedgerTraversal() {
        val entries = CountedEntries(shareBalanceFixture(20).entries)
        assertTrue(shareBalances(emptyList(), entries).isEmpty())
        assertEquals(0, entries.reads)
    }

}
