package dev.orestegabo.portfolio.investments

import kotlin.test.*

class NativeAppActionsTest {
    @Test fun externalRequestsAreWhitelistedAndConsumedOnce() {
        try {
            NativeAppActions.clear()
            assertFalse(NativeAppActions.request("delete-account"))
            assertNull(NativeAppActions.pending)
            assertTrue(NativeAppActions.request("import-pdf"))
            assertFalse(NativeAppActions.request("https://untrusted.example"))
            assertEquals("import-pdf",NativeAppActions.take())
            assertNull(NativeAppActions.take())
            assertTrue(NativeAppActions.request("calendar"))
            NativeAppActions.clear()
            assertNull(NativeAppActions.pending)
        } finally { NativeAppActions.clear() }
    }
}
