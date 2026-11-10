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
    @Test fun bidsAllowEarlyMonthAndClosingDayButNeverClosedOrUnverifiedForms() {
        val verified=form().put("bidTerms", JsonRecord().put("bondName","FXD 6/2026/5YRS").put("tenorYears",5)
            .put("issuanceDate","2026-11-11").put("settlementDate","2026-11-13").put("maturityDate","2031-11-07"))
        for(day in listOf("2026-11-01","2026-11-08","2026-11-09","2026-11-11")) assertTrue(applicationBidAllowed(verified,issue(),LocalDate.parse(day)))
        for(day in listOf("2026-10-31","2026-11-12","2027-11-01")) assertFalse(applicationBidAllowed(verified,issue(),LocalDate.parse(day)))
        assertFalse(applicationBidAllowed(form(),issue(),LocalDate.parse("2026-11-01")))
        assertFalse(applicationBidAllowed(verified,issue().put("settlementDate","2026-11-14"),LocalDate.parse("2026-11-01")))
    }

    @Test fun calendarTemporaryPurchasesDoNotDependOnFormParsingAndCloseWithTheWindow() {
        val calendarIssue = issue().put("title", "New 5-year bond")
        for (day in listOf("2026-11-01", "2026-11-08", "2026-11-11")) {
            assertTrue(calendarTemporaryPurchaseAllowed(calendarIssue, LocalDate.parse(day)))
        }
        for (day in listOf("2026-10-31", "2026-11-12", "2026-12-01", "2027-11-01")) {
            assertFalse(calendarTemporaryPurchaseAllowed(calendarIssue, LocalDate.parse(day)))
        }
        assertFalse(calendarTemporaryPurchaseAllowed(JsonRecord(), LocalDate.parse("2026-11-01")))
        val pending = pendingBondInput(calendarIssue, 1_000_000.0, LocalDate.parse("2026-11-08"))
        assertEquals("New 5-year bond", pending.text("bondName"))
        assertEquals("2026-11-13", pending.text("settlementDate"))
        assertEquals("2031-11-07", pending.text("maturityDate"))
        assertEquals("submitted", pending.text("status"))
    }

}
