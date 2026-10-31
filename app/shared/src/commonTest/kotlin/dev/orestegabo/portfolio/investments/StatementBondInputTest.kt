package dev.orestegabo.portfolio.investments

import kotlin.test.*

class StatementBondInputTest {
    @Test fun missingPrincipalOrFeesNeverBecomeZero() {
        assertNull(statementBondFigures("", "100", "0", "0", "5"))
        assertNull(statementBondFigures("1500000", "100", "", "0", "5"))
        assertNull(statementBondFigures("1500000", "100", "0", "", "5"))
    }
    @Test fun explicitFiguresRemainSeparateFromPublicTermsAndStatementDebit() {
        val result = assertNotNull(statementBondFigures("1500000", "100", "10900", "0", "5"))
        assertEquals(1500000.0, result.optDouble("faceValue"))
        assertEquals(10900.0, result.optDouble("feesPaid"))
        assertEquals(.05, result.optDouble("withholdingTaxRate"))
        assertFalse(result.has("couponRate")); assertFalse(result.has("couponDates"))
    }
    @Test fun malformedAmountsAndOverprecisionAreRejected() {
        for (bad in listOf("-1", "NaN", "1e6", "1,00,000", "1.001", "0"))
            assertNull(statementBondFigures(bad, "100", "0", "0", "5"))
        assertNull(statementBondFigures("1500000", "100", "10.001", "0", "5"))
        assertNull(statementBondFigures("1500000", "1001", "0", "0", "5"))
        assertNull(statementBondFigures("1500000", "100", "0", "0", "101"))
    }
}
