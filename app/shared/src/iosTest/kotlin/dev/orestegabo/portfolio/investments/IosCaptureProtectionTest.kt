@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class IosCaptureProtectionTest {
    @Test fun nativeShieldAndBackdropReadinessPreserveManualVisibilityDuringRecording() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        var captureAllowed = false
        var manualHidden = false
        val settings = object : ServerSettings {
            override fun readOrigin() = "https://capture.example.test"
            override fun writeOrigin(origin: String) {}
            override fun readScreenCaptureAllowed() = captureAllowed
            override fun writeScreenCaptureAllowed(allowed: Boolean) { captureAllowed = allowed }
            override fun readNumbersHidden() = manualHidden
            override fun writeNumbersHidden(hidden: Boolean) { manualHidden = hidden }
        }
        updateIosCaptureShield(available = false, captured = false)
        val model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine { respond("""{"authenticated":false}""", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) })
        })
        store.put("capture", model)
        try {
            assertTrue(model.captureProtectionEnabled)
            assertTrue(model.numbersConcealed) // Before the native host reports readiness.
            advanceUntilIdle()
            updateIosCaptureShield(available = true, captured = false)
            assertFalse(model.numbersRevealBlocked)
            assertFalse(model.numbersConcealed)
            assertTrue(model.captureProtectionEnabled) // Visible numbers do not turn protection off.
            model.toggleNumberVisibility()
            assertTrue(model.numbersHidden)
            updateIosCaptureShield(available = true, captured = true)
            assertTrue(model.numbersConcealed)
            updateIosCaptureShield(available = true, captured = false)
            assertTrue(model.numbersConcealed) // Manual privacy survives the recording ending.
            model.toggleNumberVisibility()
            assertFalse(model.numbersConcealed)
            updateIosCaptureShield(available = true, captured = true)
            assertFalse(model.numbersRevealBlocked)
            assertFalse(model.numbersConcealed) // Capture uses the redacted backdrop, not the live view.
            model.toggleNumberVisibility()
            assertTrue(model.numbersConcealed)
            assertTrue(manualHidden)
            model.toggleNumberVisibility()
            assertFalse(manualHidden)
            model.updateScreenCapturePermission(true)
            assertFalse(model.numbersConcealed) // Explicit capture opt-out is honored.
            model.updateScreenCapturePermission(false)
            updateIosCaptureShield(available = false, captured = false)
            assertTrue(model.numbersConcealed) // A missing UIKit canvas fails closed.
        } finally {
            store.clear()
            updateIosCaptureShield(available = false, captured = false)
            Dispatchers.resetMain()
        }
    }
}
