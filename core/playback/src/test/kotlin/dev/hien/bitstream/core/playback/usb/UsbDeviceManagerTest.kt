package dev.hien.bitstream.core.playback.usb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDeviceManagerTest {
    @Test
    fun remembersLastDeviceAndPrefersIt() {
        val a = handle(key = "a", vendor = 1, product = 2)
        val b = handle(key = "b", vendor = 3, product = 4)
        val manager = UsbDeviceManager(
            source = FakeSource(listOf(a, b)),
            parser = UacDescriptorParser(),
            lastKey = "b",
        )

        assertEquals("b", manager.preferredDevice()?.key)
    }

    @Test
    fun deniedPermissionLeavesStateRetryable() {
        val source = FakeSource(listOf(handle("a", 1, 2, permitted = false)))
        val manager = UsbDeviceManager(source, UacDescriptorParser())

        manager.onPermissionDenied("a")

        assertTrue(manager.state.value is DacState.PermissionNeeded)
        manager.connectByKey("a")
        assertEquals("a", source.requested)
    }

    @Test
    fun connectParsesDescriptorAndPersistsKey() = runTest {
        val handle = handle("a", 1, 2)
        val source = FakeSource(listOf(handle))
        val store = FakeStore()
        val manager = UsbDeviceManager(
            source = source,
            parser = UacDescriptorParser(),
            store = store,
            scope = managerScope(),
        )

        manager.connect(handle)

        val connected = manager.state.value as DacState.Connected
        assertEquals(42, connected.fileDescriptor)
        assertEquals("a", connected.handle.key)
        assertEquals(24, connected.device.formats.single().bitResolution)
        assertEquals(listOf(44_100, 48_000, 96_000), connected.device.formats.single().sampleRates)
        assertEquals(VolumeRange(-1536, 0, 256), connected.device.volume)
        assertEquals("a", manager.lastDeviceKey)

        assertEquals("a", store.key.value)
    }

    @Test
    fun connectWithoutPermissionRequestsIt() {
        val source = FakeSource(listOf(handle("a", 1, 2, permitted = false)))
        val manager = UsbDeviceManager(source, UacDescriptorParser())

        manager.connect(handle("a", 1, 2, permitted = false))

        val state = manager.state.value as DacState.PermissionNeeded
        assertEquals("a", state.key)
        assertEquals("a", source.requested)
        assertNull(source.opened)
    }

    @Test
    fun detachClearsConnectedStateAndRunsOnBeforeCloseBeforeSourceClose() {
        val events = mutableListOf<String>()
        val source = FakeSource(listOf(handle("a", 1, 2)), onClose = { events += "source.close" })
        val manager = UsbDeviceManager(source, UacDescriptorParser())
        manager.onBeforeClose = { events += "onBeforeClose" }
        manager.connect(handle("a", 1, 2))

        manager.onDetached("a")

        assertTrue(manager.state.value is DacState.NoDevice)
        assertEquals("a", source.closed)
        assertEquals(listOf("onBeforeClose", "source.close"), events)
    }

    @Test
    fun malformedDescriptorReportsErrorAndClosesTheDevice() {
        val handle = handle("a", 1, 2, raw = byteArrayOf(9, 2, 0))
        val source = FakeSource(listOf(handle))
        val manager = UsbDeviceManager(source, UacDescriptorParser())

        manager.connect(handle)

        assertTrue(manager.state.value is DacState.Error)
        assertEquals("a", source.closed)
    }

    @Test
    fun grantedPermissionConnectsViaEvent() = runTest {
        val source = FakeSource(listOf(handle("a", 1, 2, permitted = false)))
        val manager = UsbDeviceManager(
            source = source,
            parser = UacDescriptorParser(),
            scope = managerScope(),
        )
        source.emit(UsbEvent.PermissionResult("a", granted = true))
        val connected = manager.state.value as DacState.Connected
        assertEquals("a", connected.handle.key)
    }

    @Test
    fun attachedRememberedDeviceAutoConnects() = runTest {
        val a = handle("a", 1, 2)
        val b = handle("b", 3, 4)
        val source = FakeSource(listOf(a, b))
        val manager = UsbDeviceManager(
            source = source,
            parser = UacDescriptorParser(),
            lastKey = "b",
            scope = managerScope(),
        )
        source.emit(UsbEvent.Attached(b))
        val connected = manager.state.value as DacState.Connected
        assertEquals("b", connected.handle.key)
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private fun TestScope.managerScope(): CoroutineScope =
    CoroutineScope(UnconfinedTestDispatcher(testScheduler))

private class FakeSource(
    private val handles: List<UsbDeviceHandle>,
    private val rates: List<Int> = listOf(44_100, 48_000, 96_000),
    private val volume: VolumeRange? = VolumeRange(-1536, 0, 256),
    private val onClose: (() -> Unit)? = null,
) : UsbDeviceSource {
    private val eventFlow = MutableSharedFlow<UsbEvent>(extraBufferCapacity = 8)
    var requested: String? = null
    var opened: String? = null
    var closed: String? = null

    override fun devices(): List<UsbDeviceHandle> = handles

    override fun open(handle: UsbDeviceHandle): Int {
        opened = handle.key
        return 42
    }

    override fun close(handle: UsbDeviceHandle) {
        onClose?.invoke()
        closed = handle.key
    }

    override fun requestPermission(handle: UsbDeviceHandle) {
        requested = handle.key
    }

    override fun sampleRates(
        handle: UsbDeviceHandle,
        clockSourceId: Int,
        controlInterface: Int,
    ): List<Int> = rates

    override fun volumeRange(
        handle: UsbDeviceHandle,
        featureUnitId: Int,
        controlInterface: Int,
    ): VolumeRange? = volume

    override val events: Flow<UsbEvent> get() = eventFlow

    fun emit(event: UsbEvent) {
        eventFlow.tryEmit(event)
    }
}

private class FakeStore : LastDeviceStore {
    val key = MutableStateFlow<String?>(null)
    override val lastDeviceKey: Flow<String?> = key
    override suspend fun setLastDeviceKey(key: String?) {
        this.key.value = key
    }
}

private fun handle(
    key: String,
    vendor: Int,
    product: Int,
    permitted: Boolean = true,
    raw: ByteArray? = uac2Descriptor(),
): UsbDeviceHandle = UsbDeviceHandle(
    key = key,
    vendorId = vendor,
    productId = product,
    name = "DAC $key",
    deviceId = vendor * 100 + product,
    hasPermission = permitted,
    rawDescriptor = raw,
)

private fun uac2Descriptor(): ByteArray {
    val body = bytes(
        interfaceDescriptor(number = 0, alt = 0, endpoints = 0, subclass = 1, protocol = 0x20),
        acHeaderUac2(),
        clockSource(clockId = 10),
        featureUnit(unitId = 5),
        interfaceDescriptor(number = 1, alt = 0, endpoints = 0, subclass = 2, protocol = 0x20),
        interfaceDescriptor(number = 1, alt = 1, endpoints = 1, subclass = 2, protocol = 0x20),
        asGeneral(channels = 2),
        format(subslot = 4, bits = 24),
        endpoint(address = 0x01, attributes = 0x05, maxPacket = 0x0400, interval = 1),
    )
    val total = 9 + body.size
    return descriptor(
        9, 0x02,
        total and 0xFF, (total shr 8) and 0xFF,
        2, 1, 0, 0x80, 0x32,
    ) + body
}

private fun interfaceDescriptor(
    number: Int,
    alt: Int,
    endpoints: Int,
    subclass: Int,
    protocol: Int,
): ByteArray = descriptor(
    9, 0x04,
    number, alt, endpoints,
    0x01, subclass, protocol, 0,
)

private fun acHeaderUac2(): ByteArray = descriptor(
    9, 0x24, 0x01,
    0x00, 0x02,
    0x06,
    0x40, 0x00,
    0x00,
)

private fun clockSource(clockId: Int): ByteArray = descriptor(
    8, 0x24, 0x0A,
    clockId,
    0x01,
    0x03,
    0,
    0,
)

private fun featureUnit(unitId: Int): ByteArray = descriptor(
    18, 0x24, 0x06,
    unitId,
    1,
    0x03, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0,
)

private fun asGeneral(channels: Int): ByteArray = descriptor(
    16, 0x24, 0x01,
    1,
    0x00,
    0x01,
    0x01, 0x00, 0x00, 0x00,
    channels,
    0x03, 0x00, 0x00, 0x00,
    0,
)

private fun format(subslot: Int, bits: Int): ByteArray = descriptor(
    6, 0x24, 0x02,
    0x01,
    subslot,
    bits,
)

private fun endpoint(
    address: Int,
    attributes: Int,
    maxPacket: Int,
    interval: Int,
): ByteArray = descriptor(
    7, 0x05,
    address,
    attributes,
    maxPacket and 0xFF, (maxPacket shr 8) and 0xFF,
    interval,
)

private fun descriptor(vararg values: Int): ByteArray =
    ByteArray(values.size) { values[it].toByte() }

private fun bytes(vararg parts: ByteArray): ByteArray =
    parts.fold(ByteArray(0)) { acc, part -> acc + part }
