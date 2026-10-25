package dev.orestegabo.portfolio.investments

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShakeDetectorTest {
    private fun peak(detector: ShakeDetector, time: Long): Boolean {
        detector.sample(time - 20, 0.0, 0.0, 1.0)
        return detector.sample(time, 2.8, 0.0, 1.0)
    }

    @Test fun restingWalkingAndOneBumpDoNotToggle() {
        val detector = ShakeDetector()
        for (time in 0L..1000L step 20) assertFalse(detector.sample(time, 0.3, 0.1, 1.2))
        assertFalse(peak(detector, 1100))
        for (time in 1120L..2000L step 20) assertFalse(detector.sample(time, 0.0, 0.0, 1.0))
        assertFalse(peak(detector, 2100))
    }

    @Test fun threePeaksToggleOnceAndCooldownAllowsLaterShake() {
        val detector = ShakeDetector()
        assertFalse(peak(detector, 100))
        assertFalse(peak(detector, 250))
        assertTrue(peak(detector, 400))
        for (time in 550L..2050L step 150) assertFalse(peak(detector, time))
        assertFalse(peak(detector, 2300))
        assertFalse(peak(detector, 2450))
        assertTrue(peak(detector, 2600))
    }

    @Test fun sustainedAccelerationAndRapidSensorNoiseAreNotShakes() {
        val detector = ShakeDetector()
        for (time in 0L..2000L step 20) assertFalse(detector.sample(time, 3.0, 0.0, 1.0))
        val noisy = ShakeDetector()
        for (time in 100L..600L step 30) assertFalse(peak(noisy, time))
    }

    @Test fun sparsePeaksAndInvalidDataDoNotToggle() {
        val detector = ShakeDetector()
        assertFalse(detector.sample(0, Double.NaN, 0.0, 1.0))
        for (time in 100L..5000L step 900) assertFalse(peak(detector, time))
    }
}
