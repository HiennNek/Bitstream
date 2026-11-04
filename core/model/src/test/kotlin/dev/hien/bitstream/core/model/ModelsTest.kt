package dev.hien.bitstream.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun `audio format keeps source depth separate from subslot width`() {
        val format = AudioFormat(sampleRate = 96_000, bitDepth = 24, channels = 2)
        assertEquals(96_000, format.sampleRate)
        assertEquals(24, format.bitDepth)
        assertEquals(2, format.channels)
    }

    @Test
    fun `playback state starts idle with unavailable integrity`() {
        val state = PlaybackState()
        assertEquals(PlaybackStatus.IDLE, state.status)
        assertEquals(Integrity.UNAVAILABLE, state.integrity)
        assertEquals(BitPerfectPath.NO_DAC, state.path)
    }
}
