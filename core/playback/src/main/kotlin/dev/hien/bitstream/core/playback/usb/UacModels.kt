package dev.hien.bitstream.core.playback.usb

/** Synchronization type declared by an isochronous audio endpoint. */
enum class SyncType { ASYNC, ADAPTIVE, SYNC, NONE }

/**
 * Feature-unit volume range. UAC reports these values in 1/256 dB units, so a
 * value of 256 is 1 dB and -256 is -1 dB.
 */
data class VolumeRange(
    val min: Int,
    val max: Int,
    val resolution: Int,
)

/**
 * One Type-I PCM format an alternate setting can carry.
 *
 * UAC1 format descriptors list their discrete sample rates. UAC2 does not put
 * rates in its format descriptor; a real UAC2 device reports them through a
 * GET_RANGE control request on the clock source, so [sampleRates] stays empty
 * when only standard UAC2 descriptors are present. Some fixtures (and a few
 * vendor descriptors in the wild) append a 3-byte rate table to the format
 * descriptor; those rates are parsed here.
 */
data class UacFormat(
    val channels: Int,
    val subslotBytes: Int,
    val bitResolution: Int,
    val sampleRates: List<Int>,
)

/** The playable view of a USB Audio Class device. */
data class UacDevice(
    val uacVersion: Int,
    val controlInterface: Int,
    val streamingInterface: Int,
    val altSetting: Int,
    val endpointAddress: Int,
    val syncType: SyncType,
    val feedbackEndpoint: Int?,
    val formats: List<UacFormat>,
    val clockSourceId: Int?,
    val featureUnitId: Int?,
    val volume: VolumeRange?,
    val rawDescriptor: ByteArray,
    val maxPacketSize: Int = 0,
    val interval: Int = 0,
)
