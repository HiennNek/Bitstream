package dev.hien.bitstream.core.playback.native

/**
 * JNI surface for the native USB audio writer.
 *
 * A handle returned by [nativeOpen] owns the libusb session; [nativeClose] must
 * be called exactly once for every non-zero handle.
 */
object NativeAudio {
    init {
        System.loadLibrary("bitstream_audio")
    }

    external fun nativeOpen(
        fd: Int,
        endpoint: Int,
        interfaceNumber: Int,
        altSetting: Int,
        subslotBytes: Int,
        channels: Int,
        sampleRate: Int,
        syncType: Int,
        feedbackEndpoint: Int,
        clockSourceId: Int,
    ): Long

    /**
     * Switches the active handle's sample rate (UAC2 clock source `SET_CUR` or
     * UAC1 endpoint `SET_CUR`). Returns `0` on success or a negative error code.
     */
    external fun nativeSetSampleRate(handle: Long, rate: Int): Int

    external fun nativeStartSilence(handle: Long, frames: Long): Int

    /**
     * Starts decoding [fd] into the USB ring. The handle must have been opened
     * at the same rate, channel count, and subslot width as the file.
     */
    external fun nativePlayFile(
        handle: Long,
        fd: Int,
        startFrame: Long,
        sampleRate: Int,
        bitDepth: Int,
        channels: Int,
        subslotBytes: Int,
    ): Int

    /** Queues a same-format file for gapless playback. */
    external fun nativeEnqueueNext(
        handle: Long,
        fd: Int,
        sampleRate: Int,
        bitDepth: Int,
        channels: Int,
        subslotBytes: Int,
    ): Int

    /**
     * Sets the Q1.30 software volume multiplier (`-1` disables software volume
     * for bit-perfect output; `0..(1 shl 30)` applies Q1.30 gain with TPDF dither).
     */
    external fun nativeSetSoftwareVolume(handle: Long, gainQ30: Int)

    /**
     * Returns `[state, underruns, framesWritten, md5State, errorCode, trackIndex, ringFillPercent]`.
     * State: 0 idle, 1 playing, 2 ended, 3 error.
     * MD5: 0 unavailable/partial, 1 verified, 2 failed.
     */
    external fun nativeStats(handle: Long): LongArray

    external fun nativeStop(handle: Long)

    external fun nativeClose(handle: Long)
}
