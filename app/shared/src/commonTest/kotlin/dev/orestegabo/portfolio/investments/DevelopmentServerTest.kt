package dev.orestegabo.portfolio.investments

import kotlin.test.*

class DevelopmentServerTest {
    @Test fun releaseIgnoresDevelopmentAddress() {
        assertNull(developmentOrigin(false, "http://mac.local:8080"))
    }

    @Test fun onlyExplicitDebugConfigurationOverridesTheServer() {
        assertNull(developmentOrigin(true, null))
        assertNull(developmentOrigin(true, " "))
        assertNull(developmentOrigin(true, "$(GABO_DEVELOPMENT_API_ORIGIN)"))
        assertEquals("http://mac.local:8080", developmentOrigin(true, " http://mac.local:8080 "))
    }
}
