package dev.hien.bitstream.core.playback.usb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The DAC output state shown by the app and the USB diagnostics screen. */
sealed interface DacState {
    data object NoDevice : DacState
    data class PermissionNeeded(val key: String, val name: String) : DacState
    data class Connecting(val key: String) : DacState
    data class Connected(
        val device: UacDevice,
        val handle: UsbDeviceHandle,
        val fileDescriptor: Int,
    ) : DacState

    data class Error(val reason: String, val key: String? = null) : DacState
}

/**
 * Owns USB DAC selection, permission, descriptor parsing, and the open device
 * connection. All platform work goes through [UsbDeviceSource], so the state
 * machine is unit-testable without Android.
 */
class UsbDeviceManager(
    private val source: UsbDeviceSource,
    private val parser: UacDescriptorParser = UacDescriptorParser(),
    private val store: LastDeviceStore? = null,
    lastKey: String? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<DacState>(DacState.NoDevice)
    val state: StateFlow<DacState> = _state.asStateFlow()

    var lastDeviceKey: String? = lastKey
        private set

    private var openHandle: UsbDeviceHandle? = null
    private var pendingHandle: UsbDeviceHandle? = null

    init {
        scope.launch {
            source.events.collect { event ->
                when (event) {
                    is UsbEvent.Attached -> onAttached(event.handle)
                    is UsbEvent.Detached -> onDetached(event.key)
                    is UsbEvent.PermissionResult -> if (event.granted) {
                        onPermissionGranted(event.key)
                    } else {
                        onPermissionDenied(event.key)
                    }
                }
            }
        }
        if (lastKey == null && store != null) {
            scope.launch {
                val stored = store.lastDeviceKey.first()
                if (stored != null) {
                    lastDeviceKey = stored
                    refresh()
                }
            }
        }
    }

    fun devices(): List<UsbDeviceHandle> = source.devices()

    /** The remembered device when present, otherwise the first attached one. */
    fun preferredDevice(): UsbDeviceHandle? {
        val devices = source.devices()
        val remembered = lastDeviceKey
        return devices.firstOrNull { it.key == remembered } ?: devices.firstOrNull()
    }

    /** Re-enumerates USB devices and connects the preferred one when possible. */
    fun refresh() {
        val devices = source.devices()
        val current = _state.value
        if (current is DacState.Connected) {
            if (devices.none { it.key == current.handle.key }) disconnect()
            return
        }
        if (devices.isEmpty()) {
            if (current !is DacState.NoDevice) _state.value = DacState.NoDevice
            return
        }
        if (current is DacState.PermissionNeeded || current is DacState.Connecting) return
        preferredDevice()?.let(::connect)
    }

    fun connect(handle: UsbDeviceHandle) {
        pendingHandle = handle
        if (!handle.hasPermission) {
            _state.value = DacState.PermissionNeeded(handle.key, handle.name)
            source.requestPermission(handle)
            return
        }

        if (openHandle?.key != handle.key) {
            openHandle?.let(source::close)
            openHandle = null
        }

        _state.value = DacState.Connecting(handle.key)
        val beforeOpen = handle.rawDescriptor
        val fileDescriptor = runCatching { source.open(handle) }.getOrDefault(-1)
        if (fileDescriptor < 0) {
            _state.value = DacState.Error("Could not open ${handle.name}.", handle.key)
            return
        }
        openHandle = handle

        val raw = handle.rawDescriptor ?: beforeOpen
        val parsed = raw?.let { bytes ->
            runCatching { parser.parse(bytes) }.getOrNull()
        }
        if (parsed == null) {
            source.close(handle)
            openHandle = null
            _state.value = DacState.Error(
                "The USB audio descriptors could not be parsed.",
                handle.key,
            )
            return
        }

        val device = parsed.copy(
            formats = mergeSampleRates(parsed, handle),
            // UAC1 reports volume through GET_MIN/GET_MAX/GET_RES; only UAC2
            // uses the single GET_RANGE request implemented here.
            volume = if (parsed.uacVersion == 2) {
                parsed.featureUnitId
                    ?.let { source.volumeRange(handle, it, parsed.controlInterface) }
                    ?: parsed.volume
            } else {
                parsed.volume
            },
        )

        lastDeviceKey = handle.key
        store?.let { lastStore ->
            scope.launch { lastStore.setLastDeviceKey(handle.key) }
        }
        _state.value = DacState.Connected(device, handle, fileDescriptor)
    }

    fun connectByKey(key: String) {
        val handle = source.devices().firstOrNull { it.key == key }
            ?: pendingHandle?.takeIf { it.key == key }
            ?: return
        connect(handle)
    }

    fun disconnect() {
        openHandle?.let(source::close)
        openHandle = null
        _state.value = DacState.NoDevice
    }

    fun setHardwareVolume(level: Int): Boolean {
        val connected = _state.value as? DacState.Connected ?: return false
        val featureUnitId = connected.device.featureUnitId ?: return false
        val range = connected.device.volume ?: return false
        return source.setVolume(
            handle = connected.handle,
            featureUnitId = featureUnitId,
            controlInterface = connected.device.controlInterface,
            range = range,
            level = level.coerceIn(0, 100),
        )
    }

    fun onAttached(handle: UsbDeviceHandle) {
        if (_state.value is DacState.Connected) return
        val remembered = lastDeviceKey
        if (remembered == null || remembered == handle.key) {
            connect(handle)
        }
    }

    fun onDetached(key: String) {
        if (openHandle?.key == key) {
            openHandle?.let(source::close)
            openHandle = null
        }
        val current = _state.value
        val affectsCurrent = when (current) {
            is DacState.Connected -> current.handle.key == key
            is DacState.Connecting -> current.key == key
            is DacState.PermissionNeeded -> current.key == key
            else -> false
        }
        if (affectsCurrent) _state.value = DacState.NoDevice
    }

    fun onPermissionGranted(key: String) {
        val handle = source.devices().firstOrNull { it.key == key }
            ?: pendingHandle?.takeIf { it.key == key }
            ?: return
        connect(handle.copy(hasPermission = true))
    }

    fun onPermissionDenied(key: String) {
        val handle = source.devices().firstOrNull { it.key == key }
            ?: pendingHandle?.takeIf { it.key == key }
        _state.value = DacState.PermissionNeeded(
            key = key,
            name = handle?.name ?: "USB DAC",
        )
    }

    private fun mergeSampleRates(
        device: UacDevice,
        handle: UsbDeviceHandle,
    ): List<UacFormat> {
        if (device.formats.any { it.sampleRates.isNotEmpty() }) return device.formats
        if (device.uacVersion != 2) return device.formats
        val clock = device.clockSourceId ?: return device.formats
        val rates = source.sampleRates(handle, clock, device.controlInterface)
        if (rates.isEmpty()) return device.formats
        return device.formats.map { it.copy(sampleRates = rates) }
    }
}
