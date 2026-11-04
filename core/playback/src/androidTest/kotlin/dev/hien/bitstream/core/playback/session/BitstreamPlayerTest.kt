package dev.hien.bitstream.core.playback.session

import android.os.Looper
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.hien.bitstream.core.model.PlaybackState
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import dev.hien.bitstream.core.playback.engine.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitstreamPlayerTest {
    @Test
    fun playerStateReflectsEnginePause() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller = FakePlaybackController(
                state = PlaybackState(status = PlaybackStatus.PAUSED),
            )
            val player = BitstreamPlayer(controller, Looper.getMainLooper())
            assertEquals(Player.STATE_READY, player.playbackState)
            assertFalse(player.playWhenReady)
        }
    }

    @Test
    fun playerExposesMediaItemsAndHardwareVolumeCommand() {
        val sampleTrack = Track(
            id = 42L,
            uri = "content://bitstream/42.flac",
            title = "Blue Train",
            artist = "John Coltrane",
            album = "Blue Train",
            durationMs = 600_000L,
            sampleRate = 96_000,
            bitDepth = 24,
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller = FakePlaybackController(
                state = PlaybackState(
                    status = PlaybackStatus.READY,
                    track = sampleTrack,
                    positionMs = 12_500L,
                    durationMs = 600_000L,
                    queueSize = 1,
                    volume = VolumeState.Hardware(75),
                ),
                items = listOf(sampleTrack),
                currentIndex = 0,
            )
            val player = BitstreamPlayer(controller, Looper.getMainLooper())
            assertEquals(Player.STATE_READY, player.playbackState)
            assertTrue(player.playWhenReady)
            assertEquals(1, player.mediaItemCount)
            assertEquals("Blue Train", player.currentMediaItem?.mediaMetadata?.title?.toString())
            assertTrue(player.isCommandAvailable(Player.COMMAND_SET_VOLUME))
            assertEquals(0.75f, player.volume, 0.01f)

            player.pause()
            assertTrue(controller.pauseCalled)
        }
    }

    @Test
    fun playerOmitsSetVolumeCommandWhenOutputIsFixed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller = FakePlaybackController(
                state = PlaybackState(
                    status = PlaybackStatus.READY,
                    volume = VolumeState.Fixed,
                ),
            )
            val player = BitstreamPlayer(controller, Looper.getMainLooper())
            assertFalse(player.isCommandAvailable(Player.COMMAND_SET_VOLUME))
        }
    }
}

private class FakePlaybackController(
    state: PlaybackState = PlaybackState(),
    items: List<Track> = emptyList(),
    currentIndex: Int = -1,
) : PlaybackController {
    private val _state = MutableStateFlow(state)
    override val state: StateFlow<PlaybackState> = _state

    private val _queueItems = MutableStateFlow(items)
    override val queueItems: StateFlow<List<Track>> = _queueItems

    private val _currentQueueIndex = MutableStateFlow(currentIndex)
    override val currentQueueIndex: StateFlow<Int> = _currentQueueIndex

    var playCalled = false
    var pauseCalled = false
    var lastSeekMs: Long? = null
    var nextCalled = false
    var previousCalled = false
    var lastHardwareVolume: Int? = null

    override fun play() {
        playCalled = true
    }

    override fun pause() {
        pauseCalled = true
    }

    override fun seekTo(ms: Long) {
        lastSeekMs = ms
    }

    override fun next() {
        nextCalled = true
    }

    override fun previous() {
        previousCalled = true
    }

    override fun setHardwareVolume(level: Int) {
        lastHardwareVolume = level
    }
}
