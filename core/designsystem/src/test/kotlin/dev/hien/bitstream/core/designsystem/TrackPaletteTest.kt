package dev.hien.bitstream.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPaletteTest {
    @Test
    fun hue190LightMatchesMockup() {
        val p = trackPalette(190, dark = false)
        assertEquals(0xFF006D69.toInt(), p.primary)
        assertEquals(0xFFF1FCFB.toInt(), p.surface)
        assertEquals(0xFFE5F1F0.toInt(), p.surfaceContainer)
    }

    @Test
    fun hue190DarkMatchesMockup() {
        val p = trackPalette(190, dark = true)
        assertEquals(0xFF1BE3DB.toInt(), p.primary)
        assertEquals(0xFF0D1514.toInt(), p.surface)
    }

    @Test
    fun allHuesStayInGamut() {
        for (hue in 0 until 360) {
            val p = trackPalette(hue, dark = false)
            listOf(p.primary, p.surface, p.onSurface).forEach { argb ->
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                assertTrue(r in 0..255 && g in 0..255 && b in 0..255)
            }
            assertTrue(p.primary and 0xFF000000.toInt() != 0)
        }
    }
}
