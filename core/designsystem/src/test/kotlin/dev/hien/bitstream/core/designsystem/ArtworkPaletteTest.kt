package dev.hien.bitstream.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPaletteTest {
    @Test
    fun emptyPixelsHaveNoHue() {
        assertNull(dominantArtworkHue(IntArray(0)))
    }

    @Test
    fun greyPixelsHaveNoHue() {
        assertNull(dominantArtworkHue(intArrayOf(0xFF808080.toInt(), 0xFF101010.toInt())))
    }

    @Test
    fun transparentPixelsAreIgnored() {
        assertNull(dominantArtworkHue(intArrayOf(0x00FF0000)))
    }

    @Test
    fun pureRedIsZeroDegrees() {
        assertEquals(0, dominantArtworkHue(IntArray(64) { 0xFFFF0000.toInt() }))
    }

    @Test
    fun pureGreenIsOneHundredTwentyDegrees() {
        assertEquals(120, dominantArtworkHue(IntArray(64) { 0xFF00FF00.toInt() }))
    }

    @Test
    fun pureBlueIsTwoHundredFortyDegrees() {
        assertEquals(240, dominantArtworkHue(IntArray(64) { 0xFF0000FF.toInt() }))
    }

    @Test
    fun mixedArtworkStaysInRange() {
        val pixels = IntArray(64) { index ->
            if (index % 2 == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        val hue = dominantArtworkHue(pixels)
        assertNotNull(hue)
        assertTrue(hue in 0..359)
    }
}
