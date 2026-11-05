package dev.hien.bitstream.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackHueTest {
    @Test
    fun sameAlbumSameHue() {
        assertEquals(
            trackHue("Mira Holloway", "Paper Lanterns", "", "Paper Lanterns"),
            trackHue("Mira Holloway", "Paper Lanterns", "", "Another Song"),
        )
    }

    @Test
    fun hueIsAlwaysInRange() {
        listOf("", "a", "Blue Train", "multi word title").forEach { title ->
            assertTrue(trackHue("", "", "", title) in 0..359)
        }
    }

    @Test
    fun emptyAlbumFallsBackToArtistAndTitle() {
        assertNotEquals(trackHue("", "", "A", "Song"), trackHue("", "", "B", "Song"))
    }
}
