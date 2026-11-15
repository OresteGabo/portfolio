package dev.orestegabo.portfolio.investments

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class CaptureAttachmentTest {
    @Test fun detachRetiresTheOldRegistrationBeforeInvokingItsRenderer() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var availableDuringRelease = true
            try {
                ExperimentalCaptureBackdrop.attach { availableDuringRelease = ExperimentalCaptureBackdrop.available; true }
                ExperimentalCaptureBackdrop.detach()
                assertFalse("A retiring renderer must not remain available during its release callback", availableDuringRelease)
                assertFalse(ExperimentalCaptureBackdrop.available)
            } finally { ExperimentalCaptureBackdrop.detach() }
        }
    }

    @Test fun oldReleaseCannotDetachAReplacementUsingTheSameCallback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var releases = 0
            val draw: (android.graphics.Bitmap?) -> Boolean = { releases++; true }
            try {
                val releaseOld = ExperimentalCaptureBackdrop.attach(draw)
                val releaseNew = ExperimentalCaptureBackdrop.attach(draw)
                releaseOld()
                assertTrue(ExperimentalCaptureBackdrop.available)
                assertEquals(0, releases)
                releaseNew()
                assertFalse(ExperimentalCaptureBackdrop.available)
                assertEquals(1, releases)
                releaseOld(); releaseNew()
                assertEquals("Release handles must be idempotent", 1, releases)
            } finally { ExperimentalCaptureBackdrop.detach() }
        }
    }

    @Test fun releaseDoesNotEraseARegistrationInstalledByItsCallback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var replacementReleases = 0
            try {
                ExperimentalCaptureBackdrop.attach {
                    ExperimentalCaptureBackdrop.attach { replacementReleases++; true }
                    true
                }
                ExperimentalCaptureBackdrop.detach()
                assertTrue(ExperimentalCaptureBackdrop.available)
                ExperimentalCaptureBackdrop.detach()
                assertFalse(ExperimentalCaptureBackdrop.available)
                assertEquals(1, replacementReleases)
            } finally { ExperimentalCaptureBackdrop.detach() }
        }
    }
}
