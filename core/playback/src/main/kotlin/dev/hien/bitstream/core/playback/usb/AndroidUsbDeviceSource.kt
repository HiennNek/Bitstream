package dev.hien.bitstream.core.playback.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android USB host implementation. Keeps every open [UsbDeviceConnection]
 * alive because the native audio writer will wrap its file descriptor.
 */
class AndroidUsbDeviceSource(context: Context) : UsbDeviceSource {
    private val appContext = context.applicationContext
    private val usbManager =
        appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val eventFlow = MutableSharedFlow<UsbEvent>(extraBufferCapacity = 16)
    override val events: Flow<UsbEvent> = eventFlow.asSharedFlow()

    private val connections = mutableMapOf<String, UsbDeviceConnection>()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.usbDevice() ?: return
                    if (!device.isAudioDevice()) return
                    eventFlow.tryEmit(UsbEvent.Attached(device.toHandle()))
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.usbDevice() ?: return
                    eventFlow.tryEmit(UsbEvent.Detached(deviceKey(device)))
                }

                ACTION_USB_PERMISSION -> {
                    val device = intent.usbDevice() ?: return
                    eventFlow.tryEmit(
                        UsbEvent.PermissionResult(
                            key = deviceKey(device),
                            granted = intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED,
                                false,
                            ),
                        ),
                    )
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
    }

    override fun devices(): List<UsbDeviceHandle> =
        usbManager.deviceList.values
            .filter { it.isAudioDevice() }
            .map { it.toHandle() }
            .sortedWith(compareBy({ it.vendorId }, { it.productId }, { it.deviceId }))

    override fun open(handle: UsbDeviceHandle): Int {
        val device = findDevice(handle) ?: return -1
        connections.remove(handle.key)?.close()
        val connection = usbManager.openDevice(device) ?: return -1

        // The kernel's USB audio driver owns the audio control interface. Class
        // requests such as GET_RANGE are only answered after we claim it, so a
        // forced claim is part of opening the DAC.
        val audioControl = device.audioControlInterface()
        val claimed = audioControl != null &&
            runCatching { connection.claimInterface(audioControl, true) }.getOrDefault(false)
        if (!claimed) {
            connection.close()
            Log.w(TAG, "could not claim the audio control interface")
            return -1
        }

        handle.rawDescriptor = runCatching { connection.rawDescriptors }.getOrNull()
        Log.d(
            TAG,
            "opened fd=${connection.fileDescriptor} raw=${handle.rawDescriptor?.size}",
        )
        connections[handle.key] = connection
        return connection.fileDescriptor
    }

    override fun close(handle: UsbDeviceHandle) {
        connections.remove(handle.key)?.close()
    }

    override fun requestPermission(handle: UsbDeviceHandle) {
        val device = findDevice(handle) ?: return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        val pending = PendingIntent.getBroadcast(appContext, device.deviceId, intent, flags)
        usbManager.requestPermission(device, pending)
    }

    override fun sampleRates(
        handle: UsbDeviceHandle,
        clockSourceId: Int,
        controlInterface: Int,
    ): List<Int> {
        val connection = connections[handle.key] ?: return emptyList()
        val countBuffer = ByteArray(2)
        val countRead = controlIn(
            connection = connection,
            request = UAC2_CS_RANGE,
            value = UAC2_CS_CONTROL_SAM_FREQ shl 8,
            index = controlInterface or (clockSourceId shl 8),
            buffer = countBuffer,
        )
        if (countRead < 2) {
            Log.d(TAG, "sample rate count request failed")
            return emptyList()
        }

        val count = le16(countBuffer, 0)
        Log.d(TAG, "sample rate ranges: $count")
        if (count !in 1..MAX_RANGES) return emptyList()

        val data = ByteArray(2 + count * 12)
        val dataRead = controlIn(
            connection = connection,
            request = UAC2_CS_RANGE,
            value = UAC2_CS_CONTROL_SAM_FREQ shl 8,
            index = controlInterface or (clockSourceId shl 8),
            buffer = data,
        )
        if (dataRead < 2) return emptyList()

        val returned = le16(data, 0).coerceAtMost((dataRead - 2) / 12)
        val rates = mutableListOf<Int>()
        for (range in 0 until returned) {
            val offset = 2 + range * 12
            val min = le32(data, offset)
            val max = le32(data, offset + 4)
            val resolution = le32(data, offset + 8)
            rates += expandRange(min = min, max = max, resolution = resolution)
        }
        val result = rates.distinct().sorted()
        Log.d(TAG, "sample rates: $result")
        return result
    }

    override fun volumeRange(
        handle: UsbDeviceHandle,
        featureUnitId: Int,
        controlInterface: Int,
    ): VolumeRange? {
        val connection = connections[handle.key] ?: return null
        val data = ByteArray(2 + 3 * 2)
        val index = controlInterface or (featureUnitId shl 8)
        var read = controlIn(
            connection = connection,
            request = UAC2_CS_RANGE,
            value = UAC_FU_VOLUME shl 8,
            index = index,
            buffer = data,
        )
        if (read < data.size || le16(data, 0) == 0) {
            read = controlIn(
                connection = connection,
                request = UAC2_CS_RANGE,
                value = (UAC_FU_VOLUME shl 8) or 1,
                index = index,
                buffer = data,
            )
        }
        if (read < data.size || le16(data, 0) == 0) {
            Log.d(TAG, "volume range unavailable (read=$read)")
            return null
        }
        return VolumeRange(
            min = le16(data, 2).toShort().toInt(),
            max = le16(data, 4).toShort().toInt(),
            resolution = le16(data, 6).toShort().toInt(),
        ).also { Log.d(TAG, "volume range: $it") }
    }

    override fun setVolume(
        handle: UsbDeviceHandle,
        featureUnitId: Int,
        controlInterface: Int,
        range: VolumeRange,
        level: Int,
    ): Boolean {
        val connection = connections[handle.key] ?: return false
        val clampedLevel = level.coerceIn(0, 100)
        val span = (range.max - range.min).coerceAtLeast(0)
        val raw = range.min + ((span.toLong() * clampedLevel) / 100L).toInt()
        val res = range.resolution.coerceAtLeast(1)
        val stepped = (range.min + ((raw - range.min) / res) * res)
            .coerceIn(range.min, range.max)
        val payload = byteArrayOf(
            (stepped and 0xFF).toByte(),
            ((stepped shr 8) and 0xFF).toByte(),
        )
        val index = controlInterface or (featureUnitId shl 8)
        val master = controlOut(
            connection = connection,
            request = UAC2_CS_CUR,
            value = UAC_FU_VOLUME shl 8,
            index = index,
            buffer = payload,
        )
        if (master >= 0) return true
        val left = controlOut(
            connection = connection,
            request = UAC2_CS_CUR,
            value = (UAC_FU_VOLUME shl 8) or 1,
            index = index,
            buffer = payload,
        )
        val right = controlOut(
            connection = connection,
            request = UAC2_CS_CUR,
            value = (UAC_FU_VOLUME shl 8) or 2,
            index = index,
            buffer = payload,
        )
        return left >= 0 || right >= 0
    }

    fun close() {
        if (registered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            registered = false
        }
        connections.values.forEach { it.close() }
        connections.clear()
    }

    private fun controlIn(
        connection: UsbDeviceConnection,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray,
    ): Int {
        val result = runCatching {
            connection.controlTransfer(
                REQUEST_TYPE_IN,
                request,
                value,
                index,
                buffer,
                buffer.size,
                CONTROL_TIMEOUT_MS,
            )
        }.getOrElse { error ->
            Log.w(TAG, "control transfer threw", error)
            -1
        }
        if (result < 0) {
            Log.d(
                TAG,
                "control IN request=0x%02x value=0x%04x index=0x%04x length=%d -> %d"
                    .format(request, value, index, buffer.size, result),
            )
        }
        return result
    }

    private fun controlOut(
        connection: UsbDeviceConnection,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray,
    ): Int {
        val result = runCatching {
            connection.controlTransfer(
                REQUEST_TYPE_OUT,
                request,
                value,
                index,
                buffer,
                buffer.size,
                CONTROL_TIMEOUT_MS,
            )
        }.getOrElse { error ->
            Log.w(TAG, "control OUT transfer threw", error)
            -1
        }
        if (result < 0) {
            Log.d(
                TAG,
                "control OUT request=0x%02x value=0x%04x index=0x%04x length=%d -> %d"
                    .format(request, value, index, buffer.size, result),
            )
        }
        return result
    }

    private fun findDevice(handle: UsbDeviceHandle): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { it.deviceId == handle.deviceId }
            ?: usbManager.deviceList.values.firstOrNull {
                it.vendorId == handle.vendorId && it.productId == handle.productId
            }

    private fun UsbDevice.toHandle(): UsbDeviceHandle = UsbDeviceHandle(
        key = deviceKey(this),
        vendorId = vendorId,
        productId = productId,
        name = runCatching { productName ?: manufacturerName }.getOrNull()
            ?: "USB audio device",
        deviceId = deviceId,
        hasPermission = usbManager.hasPermission(this),
    )

    private fun UsbDevice.isAudioDevice(): Boolean = audioControlInterface() != null

    private fun UsbDevice.audioControlInterface(): UsbInterface? {
        for (index in 0 until interfaceCount) {
            val iface = getInterface(index)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                iface.interfaceSubclass == SUBCLASS_AUDIOCONTROL
            ) {
                return iface
            }
        }
        return null
    }

    private fun deviceKey(device: UsbDevice): String =
        "${device.vendorId}:${device.productId}"

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private fun expandRange(min: Int, max: Int, resolution: Int): List<Int> {
        if (min <= 0 || max < min) return emptyList()
        if (min == max) return listOf(min)
        if (resolution <= 1) {
            return COMMON_RATES.filter { it in min..max }.ifEmpty { listOf(min, max) }
        }
        val rates = mutableListOf<Int>()
        var rate = min
        while (rate <= max && rates.size < MAX_RATES_PER_RANGE) {
            rates += rate
            if (rate > Int.MAX_VALUE - resolution) break
            rate += resolution
        }
        return rates
    }

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private companion object {
        const val TAG = "BitstreamUsb"
        const val ACTION_USB_PERMISSION = "dev.hien.bitstream.USB_PERMISSION"
        const val SUBCLASS_AUDIOCONTROL = 1

        /** Device-to-host, class, interface. */
        const val REQUEST_TYPE_IN = 0xA1
        /** Host-to-device, class, interface. */
        const val REQUEST_TYPE_OUT = 0x21
        const val UAC2_CS_CUR = 0x01
        const val UAC2_CS_RANGE = 0x02
        const val UAC2_CS_CONTROL_SAM_FREQ = 0x01
        const val UAC_FU_VOLUME = 0x02

        const val CONTROL_TIMEOUT_MS = 1000
        const val MAX_RANGES = 64
        const val MAX_RATES_PER_RANGE = 64

        val COMMON_RATES = listOf(
            44_100, 48_000, 88_200, 96_000, 176_400, 192_000,
            352_800, 384_000, 705_600, 768_000,
        )
    }
}
