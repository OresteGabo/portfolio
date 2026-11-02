package dev.orestegabo.portfolio.investments

import java.net.CookieHandler
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PublicQuoteTransportTest {
    @Test fun publicTransportFailsClosedWithoutChangingAProcessGlobalCookieStore() {
        val previous = CookieHandler.getDefault()
        val handler = object : CookieHandler() {
            override fun get(uri: URI, headers: MutableMap<String, MutableList<String>>) =
                mutableMapOf("Cookie" to mutableListOf("bonds_session_v2=synthetic-private-cookie"))
            override fun put(uri: URI, headers: MutableMap<String, MutableList<String>>) {}
        }
        try {
            CookieHandler.setDefault(handler)
            assertFailsWith<IllegalStateException> { createPublicQuoteHttpEngine() }
            assertSame(handler, CookieHandler.getDefault())
        } finally { CookieHandler.setDefault(previous) }
    }
}
