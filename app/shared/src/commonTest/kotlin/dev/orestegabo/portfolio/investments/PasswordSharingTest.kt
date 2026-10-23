package dev.orestegabo.portfolio.investments

import kotlin.test.*

class PasswordSharingTest {
    @Test fun productionCredentialIsNeverSharedForAnotherOrigin() {
        assertEquals("bonds.orestegabo.dev", passwordSharingDomain(DEFAULT_ORIGIN))
        listOf("http://bonds.orestegabo.dev", "https://bonds.orestegabo.dev:8443", "https://bonds.orestegabo.dev.evil.test",
            "http://localhost:8771", "https://other.example.test").forEach { assertNull(passwordSharingDomain(it)) }
    }
}
