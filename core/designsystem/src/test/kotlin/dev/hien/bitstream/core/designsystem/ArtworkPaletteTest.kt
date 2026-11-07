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

    /**
     * Anchors use OKLCH hue degrees — the space the palette renderer consumes.
     * Pure sRGB primaries sit at ~29/142/264 degrees in OKLCH, not the HSV
     * 0/120/240; asserting those would re-encode the old extractor's hue-rotated
     * output. Binning quantizes to 10 degrees, hence the tolerance.
     */
    @Test
    fun pureRedIsOklchTwentyNineDegrees() {
        assertEquals(29.0, dominantArtworkHue(IntArray(64) { 0xFFFF0000.toInt() })!!.toDouble(), 2.0)
    }

    @Test
    fun pureGreenIsOklchOneHundredFortyTwoDegrees() {
        assertEquals(142.0, dominantArtworkHue(IntArray(64) { 0xFF00FF00.toInt() })!!.toDouble(), 2.0)
    }

    @Test
    fun pureBlueIsOklchTwoHundredSixtyFourDegrees() {
        assertEquals(264.0, dominantArtworkHue(IntArray(64) { 0xFF0000FF.toInt() })!!.toDouble(), 2.0)
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
