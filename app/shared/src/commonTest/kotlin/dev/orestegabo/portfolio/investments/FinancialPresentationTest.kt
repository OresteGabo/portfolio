package dev.orestegabo.portfolio.investments

import kotlin.test.*

class FinancialPresentationTest {
    @Test fun rwfReferenceCostsRetainRecordedFractionsAndPrivacy() {
        val old = AppLanguage.preference
        try {
            AppLanguage.select("en")
            assertEquals("RWF 36,615.6", money(36615.60))
            assertEquals("RWF 340,728.5", money(340728.50))
            assertEquals("RWF 1.01", money(1.005))
            assertEquals("RWF 0", money(-.001))
            assertEquals("RWF ••••", maskFinancialNumbers(money(36615.6)))
            AppLanguage.select("fr")
            assertEquals("RWF 36\u202f615,6", money(36615.60))
            assertEquals("RWF ••••", maskFinancialNumbers(money(36615.6)))
        } finally { AppLanguage.select(old) }
    }
    @Test fun simulatorUsesCoreGeneratedDatesAndExactAmountsWithoutChangingStoredRecord() {
        val p=JsonRecord().put("purchaseDate","2026-07-01").put("maturityDate","2033-07-31")
            .put("faceValue",2200000).put("couponRate",.115).put("withholdingTaxRate",.05).put("couponFrequency",2)
        val projected=purchaseWithProjectionDates(p)
        assertFalse(p.has("couponDates"))
        assertEquals(126500.0,projected.optDouble("grossCouponAmount"))
        assertEquals(120175.0,projected.optDouble("netCouponAmount"))
        assertEquals("2033-07-31",projected.getJSONArray("couponDates").getString(projected.getJSONArray("couponDates").length()-1))
        val official=JsonList(listOf("2027-01-15","2033-07-20"))
        p.put("couponDates",official)
        assertEquals(official.toString(),purchaseWithProjectionDates(p).getJSONArray("couponDates").toString())
    }
}
