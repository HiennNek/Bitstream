package dev.hien.bitstream.core.playback.usb

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A USB audio device as seen by the app. [rawDescriptor] is filled in by
 * [UsbDeviceSource.open] because Android only exposes the raw configuration
 * descriptor through an open [android.hardware.usb.UsbDeviceConnection].
 */
data class UsbDeviceHandle(
    val key: String,
    val vendorId: Int,
    val productId: Int,
    val name: String,
    val deviceId: Int,
    val hasPermission: Boolean,
    var rawDescriptor: ByteArray? = null,
)

/** Asynchronous USB host events delivered by a [UsbDeviceSource]. */
sealed interface UsbEvent {
    data class Attached(val handle: UsbDeviceHandle) : UsbEvent
    data class Detached(val key: String) : UsbEvent
    data class PermissionResult(val key: String, val granted: Boolean) : UsbEvent
}

/**
 * Platform boundary for USB host access. Tests use a fake implementation; the
 * app uses [AndroidUsbDeviceSource].
 */
interface UsbDeviceSource {
    fun devices(): List<UsbDeviceHandle>

    /** Opens the device and returns its file descriptor, or a negative value. */
    fun open(handle: UsbDeviceHandle): Int

    fun close(handle: UsbDeviceHandle) {}

    fun requestPermission(handle: UsbDeviceHandle) {}

    /**
     * UAC2 sample rates are not in the descriptor; they come from a GET_RANGE
     * control request on the clock source. UAC1 returns rates from the
     * descriptor and never needs this.
     */
    fun sampleRates(
        handle: UsbDeviceHandle,
        clockSourceId: Int,
        controlInterface: Int,
    ): List<Int> = emptyList()

    /** Reads the feature unit's volume range, or null when it is unavailable. */
    fun volumeRange(
        handle: UsbDeviceHandle,
        featureUnitId: Int,
        controlInterface: Int,
    ): VolumeRange? = null

    val events: Flow<UsbEvent> get() = emptyFlow()
}
