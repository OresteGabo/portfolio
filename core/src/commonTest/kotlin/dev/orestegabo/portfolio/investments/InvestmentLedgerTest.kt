package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class InvestmentLedgerTest {
    private val today = LocalDate(2026,11,8)
    private fun lot(shares: Double = 400.0, cost: Double = 36615.6) = JsonRecord().put("id","lot").put("status","active").put("ticker","IMR").put("currency","RWF").put("tradeDate","2026-01-01").put("shares",shares).put("netAmountPayable",cost)
    private fun sale(quantity: Double = 100.0) = JsonRecord().put("kind","sale").put("holdingId","lot").put("date","2026-11-01").put("currency","RWF").put("shares",quantity).put("price",110).put("fees",100).put("tax",0)
    @Test fun partialSaleKeepsOriginalAndReducesValuationCost() {
        val original=lot();val entry=validateLedgerEntry(sale(),listOf(original),emptyList(),today)
        assertEquals(10900.0,entry.optDouble("amount"));assertEquals(9153.9,entry.optDouble("allocatedCost"));assertEquals(1746.1,entry.optDouble("realizedGain"))
        val adjusted=shareBalances(listOf(original),listOf(entry))
        assertEquals(400.0,original.optDouble("shares"));assertEquals(300.0,adjusted.single().optDouble("remainingShares"));assertEquals(27461.7,adjusted.single().optDouble("remainingCost"))
        val position=PortfolioSnapshot(holdings=adjusted).sharePositions(today).single()
        assertEquals(300.0,position.shares);assertEquals(27461.7,position.cashCost)
        assertFails { validateLedgerEntry(sale(301.0),listOf(original),listOf(entry),today) }
    }
    @Test fun finalSaleConsumesRoundingRemainder() {
        val original=lot(3.0,0.01);val entries=mutableListOf<JsonRecord>()
        repeat(3) { entries+=validateLedgerEntry(sale(1.0).put("fees",0).put("price",1),listOf(original),entries,today) }
        assertEquals(0.01,entries.sumOf { it.optDouble("allocatedCost") })
        assertEquals(0.0,shareBalances(listOf(original),entries).single().optDouble("remainingCost"))
        assertTrue(PortfolioSnapshot(holdings=shareBalances(listOf(original),entries)).sharePositions(today).isEmpty())
    }
    @Test fun manySmallSalesNeverAllocateMoreThanOriginalCost() {
        val original=lot(4.0,0.02);val entries=mutableListOf<JsonRecord>()
        repeat(4) {
            entries+=validateLedgerEntry(sale(1.0).put("fees",0).put("price",1),listOf(original),entries,today)
            assertTrue(entries.last().optDouble("allocatedCost") >= 0)
            assertTrue(shareBalances(listOf(original),entries).single().optDouble("remainingCost") >= 0)
        }
        assertEquals(0.02,sumMoney(entries.map { it.optDouble("allocatedCost") }))
    }
    @Test fun invalidSalesAndForgedProfitsAreRejectedOrRecalculated() {
        val l=listOf(lot())
        for (raw in listOf(sale(0.0),sale(401.0),sale().put("fees",20000),sale().put("date","2027-01-01"),sale().put("date","2025-01-01"),sale().put("currency","USD"),sale().put("holdingId","other"))) assertFails { validateLedgerEntry(raw,l,emptyList(),today) }
        assertEquals(1746.1,validateLedgerEntry(sale().put("realizedGain",999999),l,emptyList(),today).optDouble("realizedGain"))
    }
    @Test fun cashIncludesActualEventsAndOnlyManualCouponReceipts() {
        val p=PortfolioSnapshot(holdings=listOf(lot()))
        val e=listOf(JsonRecord().put("kind","deposit").put("date","2026-01-01").put("currency","RWF").put("amount",40000),
            JsonRecord().put("kind","dividend").put("date","2026-11-01").put("currency","RWF").put("amount",500), validateLedgerEntry(sale(),p.holdings,emptyList(),today))
        val receipts=listOf("manual","automatic").map { JsonRecord().put("method",it).put("received",true).put("dueDate","2026-11-01").put("currency","RWF").put("amount",1000) }
        val cash=recordedCash(p,receipts,e,today).single()
        assertEquals(15784.4,cash.balance);assertEquals(500.0,cash.dividends);assertEquals(1746.1,cash.realizedGain)
    }
}
