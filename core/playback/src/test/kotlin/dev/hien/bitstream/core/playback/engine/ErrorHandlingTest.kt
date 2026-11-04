package dev.hien.bitstream.core.playback.engine

import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.PlaybackStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorHandlingTest {
    @Test
    fun `detach pauses and keeps the queue`() = runTest {
        val backend = FakeBackend()
        val engine = PlaybackEngine(backend, this)
        engine.setQueue(listOf(track(1), track(2)), 0)
        engine.play()
        backend.detach()
        assertEquals(PlaybackStatus.PAUSED, engine.state.first().status)
        assertEquals(2, engine.state.first().queueSize)
        assertTrue(engine.notices.value.any { it.contains("DAC disconnected") })
    }

    @Test
    fun `reconnect does not auto-play without user action`() = runTest {
        val backend = FakeBackend()
        val engine = PlaybackEngine(backend, this)
        engine.setQueue(listOf(track(1)), 0)
        engine.play()
        backend.detach()
        backend.reattach()
        assertEquals(PlaybackStatus.PAUSED, engine.state.first().status)
        assertEquals(BitPerfectPath.BIT_PERFECT, engine.state.first().path)
    }

    @Test
    fun `native error codes map to typed PlaybackError and user message`() {
        assertEquals(PlaybackError.NO_DEVICE, PlaybackError.fromNativeCode(-4))
        assertEquals(PlaybackError.BUSY, PlaybackError.fromNativeCode(-6))
        assertEquals(PlaybackError.STALL, PlaybackError.fromNativeCode(-9))
        assertEquals(PlaybackError.OVERRUN, PlaybackError.fromNativeCode(-8))
        assertEquals(PlaybackError.UNDERRUN, PlaybackError.fromNativeCode(-99))
        assertEquals(PlaybackError.PROTOCOL, PlaybackError.fromNativeCode(-7))
        assertEquals(PlaybackError.UNSUPPORTED_RATE, PlaybackError.fromNativeCode(-101))
        assertEquals(PlaybackError.UNSUPPORTED_CHANNELS, PlaybackError.fromNativeCode(-102))
        assertEquals(PlaybackError.DECODE_FAILED, PlaybackError.fromNativeCode(-200))
        assertTrue(PlaybackError.BUSY.userMessage("FiiO JA11").contains("busy", ignoreCase = true))
    }
}
