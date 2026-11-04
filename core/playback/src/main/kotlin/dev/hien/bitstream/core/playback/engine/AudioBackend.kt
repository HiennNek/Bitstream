package dev.hien.bitstream.core.playback.engine

import dev.hien.bitstream.core.model.AudioFormat
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import kotlinx.coroutines.flow.Flow

/**
 * Typed classification of native USB / FLAC errors mapped to user-facing
 * explanations.
 */
enum class PlaybackError {
    NO_DEVICE,
    BUSY,
    STALL,
    OVERRUN,
    UNDERRUN,
    PROTOCOL,
    UNSUPPORTED_RATE,
    UNSUPPORTED_CHANNELS,
    DECODE_FAILED,
    ;

    fun userMessage(dacName: String? = null, detail: String? = null): String {
        val target = dacName ?: "USB DAC"
        return when (this) {
            NO_DEVICE -> "USB DAC disconnected — playback paused."
            BUSY -> "$target is busy (claimed by another app or Android audio)."
            STALL -> "$target stalled on USB endpoint transfer; reconnect or try again."
            OVERRUN -> "$target reported a USB packet overrun."
            UNDERRUN -> "$target experienced an audio buffer underrun."
            PROTOCOL -> "$target encountered a USB protocol error."
            UNSUPPORTED_RATE -> detail ?: "$target does not support this sample rate."
            UNSUPPORTED_CHANNELS -> detail ?: "Only stereo (2-channel) FLAC files are supported."
            DECODE_FAILED -> detail ?: "Track could not be decoded."
        }
    }

    companion object {
        fun fromNativeCode(code: Int): PlaybackError = when (code) {
            -1, -101 -> UNSUPPORTED_RATE
            -2, -9 -> PROTOCOL
            -3 -> DECODE_FAILED
            -4 -> NO_DEVICE
            -5 -> BUSY
            -6 -> STALL
            -7 -> OVERRUN
            -8 -> UNDERRUN
            -102 -> UNSUPPORTED_CHANNELS
            else -> DECODE_FAILED
        }
    }
}

/**
 * Result of asking [AudioBackend.open] to prepare a DAC stream for a track and
 * its exact [AudioFormat].
 */
sealed interface OpenResult {
    data class Ready(
        val volume: VolumeState = VolumeState.Fixed,
        val dacName: String? = null,
    ) : OpenResult

    data class NoDac(
        val reason: String = "Connect a USB DAC to start bit-perfect playback.",
    ) : OpenResult

    data class UnsupportedFormat(
        val reason: String,
        val supportedRates: List<Int> = emptyList(),
    ) : OpenResult

    data class Failed(
        val reason: String,
        val error: PlaybackError = PlaybackError.DECODE_FAILED,
    ) : OpenResult
}

/** Asynchronous events emitted by an [AudioBackend] during active playback. */
sealed interface BackendEvent {
    data class Ended(
        val integrity: Integrity = Integrity.UNAVAILABLE,
        val framesWritten: Long = 0,
    ) : BackendEvent

    data class Underrun(
        val count: Long = 1,
    ) : BackendEvent

    data class Md5(
        val integrity: Integrity,
    ) : BackendEvent

    data class Failed(
        val reason: String,
        val error: PlaybackError = PlaybackError.DECODE_FAILED,
    ) : BackendEvent

    data class Detached(
        val dacName: String? = null,
    ) : BackendEvent

    data class Reattached(
        val dacName: String? = null,
    ) : BackendEvent

    data class Progress(
        val framesWritten: Long,
        val ringFrames: Long = 0,
        val sampleRate: Int = 0,
    ) : BackendEvent {
        fun positionMs(fallbackSampleRate: Int): Long {
            val rate = if (sampleRate > 0) sampleRate else fallbackSampleRate
            if (rate <= 0) return 0L
            val playedFrames = (framesWritten - ringFrames).coerceAtLeast(0L)
            return (playedFrames * 1_000L) / rate
        }
    }
}

/**
 * Abstraction over the native USB audio writer and FLAC decoder so
 * [PlaybackEngine] is deterministically unit-testable without hardware or JNI.
 */
interface AudioBackend {
    val events: Flow<BackendEvent>

    suspend fun open(track: Track, format: AudioFormat): OpenResult

    suspend fun start(uri: String, startFrame: Long)

    /**
     * Pre-queues [track] for gapless transition when [format] matches the active
     * stream's format. Returns `true` when the backend accepted the track.
     */
    suspend fun enqueueNext(track: Track, format: AudioFormat): Boolean = false

    suspend fun pause()

    suspend fun resume()

    suspend fun stop()

    suspend fun close()

    fun setSoftwareVolume(enabled: Boolean)

    fun setSoftwareVolumeLevel(level: Int) {}

    fun setHardwareVolume(level: Int) {}
}
