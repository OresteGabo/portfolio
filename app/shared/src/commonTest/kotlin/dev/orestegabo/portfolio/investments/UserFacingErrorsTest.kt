@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class UserFacingErrorsTest {
    private val diagnostic = "NSURLErrorDomain -1004 PRIVATE_ERROR_SENTINEL https://private.test/api?token=SECRET\n at app.kt:91"

    @Test fun exceptionsAndServerDiagnosticsNeverBecomeDisplayText() {
        for (failure in listOf(
            IllegalStateException(diagnostic),
            IllegalArgumentException("Unexpected JSON token: $diagnostic"),
            ApiException(503, "<html>Stack trace: $diagnostic</html>"),
            ApiException(422, "Database constraint failed: $diagnostic"),
            ApiException(401, diagnostic),
        )) {
            val message = userErrorMessage(failure)
            assertFalse(message.contains("PRIVATE_ERROR_SENTINEL"))
            assertFalse(message.contains("SECRET"))
            assertFalse(message.contains("app.kt"))
            assertFalse(message.contains("https://"))
            assertFalse(message.contains("<html>"))
        }
        assertEquals(GENERAL_ERROR_MESSAGE, safeErrorMessage(diagnostic))
        assertEquals(GENERAL_ERROR_MESSAGE, safeErrorMessage(null, diagnostic))
    }

    @Test fun knownValidationAndAlreadySavedWarningsRemainActionable() {
        assertEquals("Use an amount in RWF 100,000 increments.", userErrorMessage(
            IllegalArgumentException("Use an amount in RWF 100,000 increments.")))
        assertEquals("Check the purchase details.", userErrorMessage(
            IllegalArgumentException(diagnostic), "Check the purchase details."))
        val saved = "Share purchase saved. Could not refresh the portfolio. Pull down on Portfolio to update; do not submit it again."
        assertEquals(saved, userErrorMessage(IllegalStateException(saved)))
        assertEquals("Email or password is incorrect.", userErrorMessage(ApiException(401, "Invalid credentials")))
        assertEquals("Too many attempts. Please wait and try again.", userErrorMessage(ApiException(429, diagnostic)))
    }

    @Test fun pdfErrorsAndProgressDoNotEchoParserOrDocumentDiagnostics() {
        assertEquals("Could not read this PDF.", userErrorMessage(IllegalArgumentException(diagnostic), "Could not read this PDF."))
        assertEquals("", safePdfProgressDetail(diagnostic))
        assertEquals("Reading page 2 of 3…", safePdfProgressDetail("Reading page 2 of 3…"))
        assertEquals("2 / 4", safePdfProgressDetail("2 / 4"))
        assertEquals("", safePdfProgressDetail("Reading page 2 of 3… $diagnostic"))
    }

    @Test fun connectionAndGenericFallbacksAreTranslatedIntoFrench() {
        for (message in listOf(CONNECTION_ERROR_MESSAGE, GENERAL_ERROR_MESSAGE,
            userErrorMessage(ApiException(503, diagnostic)), userErrorMessage(ApiException(429, diagnostic)))) {
            assertNotEquals(message, localized(message, "fr"))
        }
    }

    @Test fun startupFailuresAndDirectErrorAssignmentsAreSanitizedBeforeLogin() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            for (mode in listOf("transport", "json", "server")) {
                val store = ViewModelStore()
                val engine = MockEngine(MockEngineConfig().apply {
                    this.dispatcher = dispatcher
                    addHandler { _ -> when (mode) {
                    "transport" -> throw IllegalStateException(diagnostic)
                    "json" -> respond("<html>$diagnostic</html>", HttpStatusCode.OK)
                    else -> respond(JsonRecord().put("error", diagnostic).toString(), HttpStatusCode.ServiceUnavailable)
                    } }
                })
                val model = PortfolioModel(object : ServerSettings {
                    override fun readOrigin() = "https://private.test"
                    override fun writeOrigin(origin: String) = Unit
                }, assets = { "[]".encodeToByteArray() }, apiFactory = { PortfolioApi(it, engine) })
                store.put("model", model)
                try {
                    advanceUntilIdle()
                    assertNull(model.user)
                    assertEquals(if (mode == "server") "The service is temporarily unavailable. Please try again later."
                        else CONNECTION_ERROR_MESSAGE, model.error)
                    assertFalse(model.error.orEmpty().contains("PRIVATE_ERROR_SENTINEL"))
                    model.error = diagnostic
                    assertEquals(GENERAL_ERROR_MESSAGE, model.error)
                    model.error = null
                    assertNull(model.error)
                } finally { store.clear() }
            }
        } finally { Dispatchers.resetMain() }
    }
}
