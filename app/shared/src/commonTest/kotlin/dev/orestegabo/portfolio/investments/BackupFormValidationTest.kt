package dev.orestegabo.portfolio.investments

import kotlin.test.*

class BackupFormValidationTest {
    @Test fun exportUsesExistingEnvelopeLimitsAndRequiresAnExactConfirmation() {
        for (length in listOf(12, 256)) {
            val password = "x".repeat(length)
            assertTrue(backupFormErrors(password, password, exporting = true).valid)
        }
        for (length in listOf(0, 11, 257)) {
            val password = "x".repeat(length)
            assertNotNull(backupFormErrors(password, password, exporting = true).password)
        }
        val password = "synthetic password"
        val mismatch = backupFormErrors(password, "Synthetic password", exporting = true)
        assertNull(mismatch.password); assertNotNull(mismatch.confirmation)
        assertTrue(backupFormErrors("x".repeat(10) + "  ", "x".repeat(10) + "  ", true).valid, "Passwords must not be trimmed")
        assertNotNull(backupFormErrors(password + " ", password, true).confirmation)
    }

    @Test fun restoreDoesNotRequireTheExportOnlyConfirmation() {
        val errors = backupFormErrors("synthetic password", "", exporting = false)
        assertTrue(errors.valid)
        assertNotNull(backupFormErrors("short", "", exporting = false).password)
    }
}
