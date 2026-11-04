package dev.hien.bitstream.core.model

/**
 * The exact format of a source stream. [bitDepth] is the number of significant
 * bits in the source; it is deliberately separate from the USB subslot width,
 * which is a property of the DAC's endpoint, not of the recording.
 */
data class AudioFormat(
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int = 2,
)

/** A single indexed FLAC file. All URIs are strings so this module stays Android-free. */
data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val durationMs: Long = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 2,
    val hasMd5: Boolean = false,
    val artworkUri: String? = null,
    val year: Int? = null,
    val genre: String = "",
    val folder: String = "",
    val size: Long = 0,
    val modified: Long = 0,
) {
    val format: AudioFormat
        get() = AudioFormat(sampleRate = sampleRate, bitDepth = bitDepth, channels = channels)
}

data class Album(
    val id: String,
    val title: String,
    val albumArtist: String,
    val artworkUri: String? = null,
    val trackCount: Int = 0,
    val year: Int? = null,
)

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val artworkUri: String? = null,
)

enum class PlaybackStatus {
    IDLE,
    OPENING,
    READY,
    PAUSED,
    ENDED,
    ERROR,
    NO_DAC,
}

/** How the samples reached the DAC. Anything but [BIT_PERFECT] is a demotion. */
enum class BitPerfectPath {
    BIT_PERFECT,
    SOFTWARE_VOLUME,
    NO_DAC,
    UNSUPPORTED_FORMAT,
}

/** Result of the FLAC STREAMINFO MD5 check. */
enum class Integrity {
    UNAVAILABLE,
    PARTIAL,
    VERIFIED,
    FAILED,
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

sealed interface VolumeState {
    /** The DAC exposes no controllable volume; output is fixed. */
    data object Fixed : VolumeState

    /** Hardware volume, 0..100. */
    data class Hardware(val level: Int) : VolumeState

    /** Software volume is enabled (0..100) and therefore no longer bit-perfect. */
    data class Software(val level: Int = 100) : VolumeState
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val path: BitPerfectPath = BitPerfectPath.NO_DAC,
    val integrity: Integrity = Integrity.UNAVAILABLE,
    val track: Track? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val underruns: Long = 0,
    val queueSize: Int = 0,
    val volume: VolumeState? = null,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
)
