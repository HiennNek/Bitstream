package dev.hien.bitstream.ui.nowplaying

import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.PlaybackState
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import dev.hien.bitstream.core.playback.engine.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingViewModelTest {
    @Test
    fun `state exposes exact source format`() = runTest {
        val vm = NowPlayingViewModel(FakeEngine(state = state(rate = 192_000, depth = 24)))
        assertEquals("24/192", vm.state.first().formatLabel)
    }

    @Test
    fun `state formats fractional kHz sample rate`() = runTest {
        val vm = NowPlayingViewModel(FakeEngine(state = state(rate = 44_100, depth = 16)))
        assertEquals("16/44.1", vm.state.first().formatLabel)
    }

    @Test
    fun `transport and volume actions delegate to controller`() = runTest {
        val engine = FakeEngine(state = state(rate = 96_000, depth = 24, status = PlaybackStatus.READY))
        val vm = NowPlayingViewModel(engine)
        vm.togglePlayPause()
        assertTrue(engine.pauseCalled)
        vm.seekTo(12_000L)
        assertEquals(12_000L, engine.seekMs)
        vm.setHardwareVolume(75)
        assertEquals(75, engine.hardwareVolume)
        vm.setSoftwareVolume(true)
        assertEquals(true, engine.softwareVolume)
        vm.setSoftwareVolumeLevel(65)
        assertEquals(65, engine.softwareVolumeLevel)
    }

    private fun state(
        rate: Int,
        depth: Int,
        status: PlaybackStatus = PlaybackStatus.READY,
    ): PlaybackState = PlaybackState(
        status = status,
        path = BitPerfectPath.BIT_PERFECT,
        integrity = Integrity.VERIFIED,
        track = Track(
            id = 1L,
            uri = "content://tracks/1",
            title = "Test Track",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180_000L,
            sampleRate = rate,
            bitDepth = depth,
            channels = 2,
            hasMd5 = true,
        ),
        positionMs = 5_000L,
        durationMs = 180_000L,
        underruns = 0L,
        queueSize = 3,
        volume = VolumeState.Hardware(60),
    )

    private class FakeEngine(
        state: PlaybackState,
        dacName: String? = "FiiO JA11",
    ) : PlaybackController {
        override val state: StateFlow<PlaybackState> = MutableStateFlow(state)
        override val dacName: StateFlow<String?> = MutableStateFlow(dacName)
        var playCalled = false
        var pauseCalled = false
        var seekMs: Long? = null
        var hardwareVolume: Int? = null
        var softwareVolume: Boolean? = null
        var softwareVolumeLevel: Int? = null

        override fun play() {
            playCalled = true
        }

        override fun pause() {
            pauseCalled = true
        }

        override fun seekTo(ms: Long) {
            seekMs = ms
        }

        override fun next() = Unit

        override fun previous() = Unit

        override fun setHardwareVolume(level: Int) {
            hardwareVolume = level
        }

        override fun setSoftwareVolume(on: Boolean) {
            softwareVolume = on
        }

        override fun setSoftwareVolumeLevel(level: Int) {
            softwareVolumeLevel = level
        }

        override fun setShuffle(on: Boolean) = Unit

        override fun setRepeat(mode: RepeatMode) = Unit
    }
}
