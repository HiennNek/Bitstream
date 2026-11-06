package dev.hien.bitstream.ui.components

import dev.hien.bitstream.core.designsystem.artSides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverArtShapeTest {
    @Test
    fun sameSeedAlwaysPicksTheSameShape() {
        for (seed in 0L..99L) {
            assertEquals(artSides(seed), artSides(seed))
        }
    }

    @Test
    fun lobeCountStaysInTheMockupRange() {
        for (seed in -50L..500L) {
            assertTrue(artSides(seed) in 6..14)
        }
    }
}
