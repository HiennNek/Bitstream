package dev.hien.bitstream.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformSeekTest {
    @Test
    fun zeroDurationIsZero() {
        assertEquals(0f, seekProgress(positionMs = 1_000, durationMs = 0), 0f)
    }

    @Test
    fun halfwayIsHalf() {
        assertEquals(0.5f, seekProgress(positionMs = 30_000, durationMs = 60_000), 0.0001f)
    }

    @Test
    fun pastEndClamps() {
        assertEquals(1f, seekProgress(positionMs = 90_000, durationMs = 60_000), 0f)
    }

    @Test
    fun negativePositionClamps() {
        assertEquals(0f, seekProgress(positionMs = -1, durationMs = 60_000), 0f)
    }
}
