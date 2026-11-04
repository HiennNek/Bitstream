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

    external fun nativeStartSilence(handle: Long, frames: Long): Int

    external fun nativeStop(handle: Long)

    external fun nativeClose(handle: Long)
}
