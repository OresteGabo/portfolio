package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class InvestmentLedgerPresentationTest {
    @Test fun annualClosingQuantitiesUseSalesOnlyThroughTheYearCutoff() {
        val lot=guestShareFixture().put("id","lot").put("tradeDate","2025-07-15").put("netAmountPayable",36615.6)
        val sale=JsonRecord().put("kind","sale").put("holdingId","lot").put("date","2026-11-01").put("shares",100).put("allocatedCost",9153.9)
        val data=PortfolioReportData(PortfolioSnapshot(holdings=shareBalances(listOf(lot),listOf(sale))),null,emptyList(),"synthetic@example.test",100000,LocalDate(2026,11,8),"en",ledgerEntries=listOf(sale))
        assertEquals(400.0,annualReportScope(data,2025).closingShares.single().optDouble("shares"))
        assertEquals(300.0,annualReportScope(data,2026).closingShares.single().optDouble("shares"))
        assertEquals(27461.7,annualReportScope(data,2026).closingShares.single().optDouble("netAmountPayable"))
    }
    @Test fun activityUsesOnlyLinkedRecordsAndDistinguishesAutomaticCouponMarkings() {
        val bond=guestBondFixture().put("id","bond")
        val receipts=listOf(JsonRecord().put("purchaseId","bond").put("dueDate","2027-01-15").put("amount",120175).put("currency","RWF").put("received",true).put("method","automatic"),
            JsonRecord().put("purchaseId","other").put("dueDate","2027-01-15").put("amount",999).put("currency","RWF").put("received",true).put("method","manual"))
        val timeline=investmentActivities(listOf(bond),emptyList(),receipts,emptyList())
        assertEquals(2,timeline.size)
        assertEquals("Coupon marked automatically",timeline.first().title)
        assertEquals("Purchased",timeline.last().title)
    }
}
