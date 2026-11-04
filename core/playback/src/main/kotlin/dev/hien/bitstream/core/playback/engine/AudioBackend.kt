package dev.hien.bitstream.core.playback.engine

import dev.hien.bitstream.core.model.AudioFormat
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import kotlinx.coroutines.flow.Flow

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

    fun setHardwareVolume(level: Int) {}
}
