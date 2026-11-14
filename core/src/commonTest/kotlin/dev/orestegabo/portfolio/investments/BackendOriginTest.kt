package dev.orestegabo.portfolio.investments

import kotlin.test.*

class BackendOriginTest {
    @Test fun nativeProductionDefaultIsTheBackendServiceRatherThanAWebsite() {
        assertEquals("https://gabo-portfolio-api-639858675564.europe-west1.run.app", DEFAULT_ORIGIN)
        assertEquals(DEFAULT_ORIGIN, nativeBackendOrigin(null))
        assertEquals(DEFAULT_ORIGIN, nativeBackendOrigin(DEFAULT_ORIGIN))
    }
    @Test fun onlyExactLegacyProductionOriginsSwitchToTheBackend() {
        listOf("https://bonds.orestegabo.dev", "https://invest.orestegabo.dev/", " HTTPS://BONDS.ORESTEGABO.DEV/ ").forEach {
            assertTrue(isLegacyWebsiteApiOrigin(it)); assertEquals(DEFAULT_ORIGIN, nativeBackendOrigin(it))
        }
        listOf("https://bonds.orestegabo.dev.evil.test", "https://bonds.orestegabo.dev:8443", "http://bonds.orestegabo.dev",
            "https://bonds.orestegabo.dev/private", "https://structcapital.net", "http://localhost:8080", "https://custom.example.test").forEach {
            assertFalse(isLegacyWebsiteApiOrigin(it)); assertEquals(it, nativeBackendOrigin(it))
        }
    }
}
