package dev.hien.bitstream.ui.nowplaying

import androidx.lifecycle.ViewModel
import dev.hien.bitstream.core.model.AudioFormat
import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.PlaybackState
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import dev.hien.bitstream.core.playback.engine.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class NowPlayingUiState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val track: Track? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val format: AudioFormat? = null,
    val formatLabel: String = "",
    val path: BitPerfectPath = BitPerfectPath.NO_DAC,
    val integrity: Integrity = Integrity.UNAVAILABLE,
    val underruns: Long = 0L,
    val dacName: String? = null,
    val volume: VolumeState? = null,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val queueSize: Int = 0,
    val notices: List<String> = emptyList(),
) {
    val positionMs: Long
        get() = position

    val durationMs: Long
        get() = duration

    val isPlaying: Boolean
        get() = status == PlaybackStatus.READY
}

/**
 * Formats bit depth and sample rate as `depth/kHz`, e.g. `24/192` or `16/44.1`.
 */
fun formatSourceRateDepth(bitDepth: Int, sampleRate: Int): String {
    if (bitDepth <= 0 || sampleRate <= 0) return ""
    val wholeKhz = sampleRate / 1_000
    val remainder = (sampleRate % 1_000) / 100
    val rateText = if (remainder == 0) "$wholeKhz" else "$wholeKhz.$remainder"
    return "$bitDepth/$rateText"
}

class NowPlayingViewModel(
    private val controller: PlaybackController,
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val _state = MutableStateFlow(
        buildUiState(
            playback = controller.state.value,
            dacName = controller.dacName.value,
            notices = controller.notices.value,
        ),
    )
    val state: StateFlow<NowPlayingUiState> = _state.asStateFlow()

    init {
        vmScope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                controller.state,
                controller.dacName,
                controller.notices,
                ::buildUiState,
            ).collect { uiState ->
                _state.value = uiState
            }
        }
    }

    fun play() {
        controller.play()
    }

    fun pause() {
        controller.pause()
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(ms: Long) {
        controller.seekTo(ms)
    }

    fun next() {
        controller.next()
    }

    fun previous() {
        controller.previous()
    }

    fun toggleShuffle() {
        controller.setShuffle(!_state.value.shuffle)
    }

    fun cycleRepeat() {
        val nextMode = when (_state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        controller.setRepeat(nextMode)
    }

    fun setHardwareVolume(level: Int) {
        controller.setHardwareVolume(level)
    }

    fun setSoftwareVolume(enabled: Boolean) {
        controller.setSoftwareVolume(enabled)
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }

    private companion object {
        fun buildUiState(
            playback: PlaybackState,
            dacName: String?,
            notices: List<String>,
        ): NowPlayingUiState {
            val track = playback.track
            val format = track?.format?.takeIf { it.sampleRate > 0 && it.bitDepth > 0 }
            val formatLabel = if (format != null) {
                formatSourceRateDepth(format.bitDepth, format.sampleRate)
            } else {
                ""
            }
            return NowPlayingUiState(
                status = playback.status,
                track = track,
                position = playback.positionMs,
                duration = playback.durationMs.takeIf { it > 0L } ?: (track?.durationMs ?: 0L),
                format = format,
                formatLabel = formatLabel,
                path = playback.path,
                integrity = playback.integrity,
                underruns = playback.underruns,
                dacName = dacName,
                volume = playback.volume,
                shuffle = playback.shuffle,
                repeat = playback.repeat,
                queueSize = playback.queueSize,
                notices = notices,
            )
        }
    }
}
