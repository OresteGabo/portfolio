package dev.orestegabo.portfolio.investments

import kotlin.test.*

class IosCaptureRefreshTest {
    @Test fun continuousScrollingDefersCaptureUntilDrawingSettles() {
        val refresh = IosCaptureRefresh()
        assertNull(refresh.pendingFrame(1_000)) // No recorded scene yet.
        for (time in 0L..1_000L step 16L) {
            refresh.drawn(7L, time)
            assertNull(refresh.pendingFrame(time + 15L))
        }
        assertNull(refresh.pendingFrame(1_151L))
        assertNotNull(refresh.pendingFrame(1_152L))
        assertEquals(7L, refresh.recordedRevision)
    }

    @Test fun publishingAnIdleSceneDoesNotScheduleAnotherCapture() {
        val refresh = IosCaptureRefresh()
        refresh.drawn(1L, 0L)
        val frame = assertNotNull(refresh.pendingFrame(160L))
        assertTrue(refresh.isCurrent(frame))
        refresh.published(frame)
        assertNull(refresh.pendingFrame(10_000L))
    }

    @Test fun scrollingCachedChildLayersAlsoDefersAndSupersedesCaptures() {
        val refresh = IosCaptureRefresh()
        refresh.drawn(1L, 0L)
        val oldFrame = assertNotNull(refresh.pendingFrame(160L))
        // No parent redraw is needed for a cached lazy-list layer to move.
        for (time in 170L..1_170L step 16L) {
            refresh.changed(time)
            assertNull(refresh.pendingFrame(time + 15L))
        }
        assertFalse(refresh.isCurrent(oldFrame))
        assertNotNull(refresh.pendingFrame(1_330L))
        assertEquals(1L, refresh.recordedRevision)
    }

    @Test fun drawingDuringBackgroundConversionSupersedesTheOldImage() {
        val refresh = IosCaptureRefresh()
        refresh.drawn(1L, 0L)
        val oldFrame = assertNotNull(refresh.pendingFrame(160L))
        refresh.drawn(2L, 170L)
        assertFalse(refresh.isCurrent(oldFrame))
        assertNull(refresh.pendingFrame(329L))
        val nextFrame = assertNotNull(refresh.pendingFrame(330L))
        assertTrue(refresh.isCurrent(nextFrame))
        assertNotEquals(oldFrame, nextFrame)
    }
}
