package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class BondApplicationFormsTest {
    private fun issue() = JsonRecord().put("id", "2026-11-5y-new").put("tenorYears", 5)
        .put("openBookDate", "2026-11-09").put("closingBookDate", "2026-11-11")
        .put("settlementDate", "2026-11-13").put("maturityDate", "2031-11-07")
    private fun form() = JsonRecord().put("id", "shared-form").put("category", BOND_APPLICATION_CATEGORY)
        .put("applicationForm", JsonRecord().put("calendarId", "2026-11-5y-new").put("tenorYears", 5)
            .put("settlementDate", "2026-11-13").put("maturityDate", "2031-11-07"))

    @Test fun availableFromFirstDayOfIssuanceMonthBeforeBidWindowOpens() {
        assertFalse(bondApplicationAvailable(issue(), LocalDate.parse("2026-10-31")))
        assertTrue(bondApplicationAvailable(issue(), LocalDate.parse("2026-11-01")))
        assertTrue(bondApplicationAvailable(issue(), LocalDate.parse("2026-11-08")))
        assertTrue(bondApplicationAvailable(issue(), LocalDate.parse("2026-11-09")))
        assertTrue(bondApplicationAvailable(issue(), LocalDate.parse("2026-12-01")))
        assertFalse(bondApplicationAvailable(JsonRecord(), LocalDate.parse("2026-11-08")))
    }

    @Test fun sharedPublicationPermissionDoesNotGrantApplicationFormUploadRights() {
        assertFalse(mayUploadBondApplication(null))
        assertFalse(mayUploadBondApplication(JsonRecord().put("isOwner", false).put("canPublishPublicData", true)))
        assertTrue(mayUploadBondApplication(JsonRecord().put("isOwner", true)))
    }

    @Test fun oneCanonicalFormMatchesTheAuctionRatherThanAnInvestorOrSecurityReopening() {
        val shared = form()
        assertSame(shared, bondApplicationFor(issue(), listOf(shared)))
        assertNull(bondApplicationFor(issue().put("settlementDate", "2026-12-18"), listOf(shared)))
        assertNull(bondApplicationFor(issue().put("maturityDate", "2031-11-14"), listOf(shared)))
        assertNull(bondApplicationFor(issue().put("tenorYears", 7), listOf(shared)))
        assertNull(bondApplicationFor(issue(), listOf(shared.put("category", "Bond confirmation"))))
        assertNull(bondApplicationFor(JsonRecord(), listOf(form())))
    }

    @Test fun missingOrAmbiguousFormsNeverDownloadAnArbitraryPdf() {
        assertNull(bondApplicationFor(issue(), emptyList()))
        assertNull(bondApplicationFor(issue(), listOf(form(), form().put("id", "unexpected-duplicate"))))
        assertNull(bondApplicationFor(issue(), listOf(JsonRecord().put("category", BOND_APPLICATION_CATEGORY))))
    }
}
