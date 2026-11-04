package dev.hien.bitstream.core.playback.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UacDescriptorParserTest {
    @Test
    fun uac2PicksTheIsochronousOutAltSettingAndRates() {
        val device = UacDescriptorParser().parse(uac2Descriptors())!!

        assertEquals(2, device.uacVersion)
        assertEquals(0x01, device.endpointAddress)
        assertEquals(1, device.altSetting)
        assertEquals(1, device.streamingInterface)
        assertEquals(
            listOf(44_100, 48_000, 96_000, 192_000, 384_000),
            device.formats.single().sampleRates,
        )
        assertEquals(4, device.formats.single().subslotBytes)
        assertEquals(24, device.formats.single().bitResolution)
        assertEquals(2, device.formats.single().channels)
    }

    @Test
    fun uac2FindsAsyncFeedbackEndpointAndClockSource() {
        val device = UacDescriptorParser().parse(uac2Descriptors())!!

        assertEquals(SyncType.ASYNC, device.syncType)
        assertEquals(0x82, device.feedbackEndpoint)
        assertEquals(10, device.clockSourceId)
        assertEquals(5, device.featureUnitId)
        assertEquals(0, device.controlInterface)
    }

    @Test
    fun uac2StandardFormatWithoutRateTableHasNoRates() {
        val device = UacDescriptorParser().parse(uac2Descriptors(withDescriptorRates = false))!!

        assertEquals(2, device.uacVersion)
        assertEquals(2, device.formats.single().channels)
        assertEquals(4, device.formats.single().subslotBytes)
        assertEquals(24, device.formats.single().bitResolution)
        assertTrue(device.formats.single().sampleRates.isEmpty())
    }

    @Test
    fun uac1ReadsEndpointSampleRates() {
        val device = UacDescriptorParser().parse(uac1Descriptors())!!

        assertEquals(1, device.uacVersion)
        assertEquals(1, device.altSetting)
        assertEquals(listOf(44_100, 48_000), device.formats.single().sampleRates)
        assertEquals(2, device.formats.single().subslotBytes)
        assertEquals(16, device.formats.single().bitResolution)
        assertEquals(SyncType.ASYNC, device.syncType)
        assertEquals(0x82, device.feedbackEndpoint)
        assertEquals(5, device.featureUnitId)
    }

    @Test
    fun malformedDescriptorsReturnNull() {
        assertNull(UacDescriptorParser().parse(ByteArray(0)))
        assertNull(UacDescriptorParser().parse(byteArrayOf(9, 2, 0, 0)))
        assertNull(UacDescriptorParser().parse(uac2Descriptors() + byteArrayOf(0, 0)))
    }
}

private fun uac2Descriptors(withDescriptorRates: Boolean = true): ByteArray {
    val body = bytes(
        interfaceAssociation(firstInterface = 0, interfaceCount = 2, protocol = 0x20),
        interfaceDescriptor(
            number = 0,
            alt = 0,
            endpoints = 0,
            subclass = 1,
            protocol = 0x20,
        ),
        acHeaderUac2(),
        uac2InputTerminal(),
        uac2OutputTerminal(),
        uac2FeatureUnit(),
        uac2ClockSource(clockId = 10),
        interfaceDescriptor(
            number = 1,
            alt = 0,
            endpoints = 0,
            subclass = 2,
            protocol = 0x20,
        ),
        interfaceDescriptor(
            number = 1,
            alt = 1,
            endpoints = 2,
            subclass = 2,
            protocol = 0x20,
        ),
        uac2AsGeneral(channels = 2),
        if (withDescriptorRates) {
            uac1Format(
                channels = 2,
                subslot = 4,
                bits = 24,
                rates = listOf(44_100, 48_000, 96_000, 192_000, 384_000),
            )
        } else {
            uac2Format(subslot = 4, bits = 24)
        },
        endpoint(
            address = 0x01,
            attributes = 0x05,
            maxPacket = 0x0400,
            interval = 1,
        ),
        classSpecificEndpoint(),
        endpoint(
            address = 0x82,
            attributes = 0x11,
            maxPacket = 4,
            interval = 1,
        ),
    )

    return configuration(interfaceCount = 2, body = body)
}

private fun uac1Descriptors(): ByteArray {
    val body = bytes(
        interfaceDescriptor(
            number = 0,
            alt = 0,
            endpoints = 0,
            subclass = 1,
            protocol = 0,
        ),
        acHeaderUac1(streamingInterface = 1),
        uac1InputTerminal(),
        uac1OutputTerminal(),
        uac1FeatureUnit(),
        interfaceDescriptor(
            number = 1,
            alt = 0,
            endpoints = 0,
            subclass = 2,
            protocol = 0,
        ),
        interfaceDescriptor(
            number = 1,
            alt = 1,
            endpoints = 1,
            subclass = 2,
            protocol = 0,
        ),
        uac1AsGeneral(terminalLink = 1),
        uac1Format(
            channels = 2,
            subslot = 2,
            bits = 16,
            rates = listOf(44_100, 48_000),
        ),
        endpoint(
            address = 0x01,
            attributes = 0x05,
            maxPacket = 0x00C0,
            interval = 1,
            synchAddress = 0x82,
        ),
        endpoint(
            address = 0x82,
            attributes = 0x01,
            maxPacket = 4,
            interval = 1,
        ),
    )

    return configuration(interfaceCount = 2, body = body)
}

private fun configuration(interfaceCount: Int, body: ByteArray): ByteArray {
    val total = 9 + body.size
    val header = descriptor(
        9, 0x02,
        total and 0xFF, (total shr 8) and 0xFF,
        interfaceCount, 1, 0, 0x80, 0x32,
    )
    return header + body
}

private fun interfaceAssociation(
    firstInterface: Int,
    interfaceCount: Int,
    protocol: Int,
): ByteArray = descriptor(
    8, 0x0B,
    firstInterface, interfaceCount,
    0x01, 0x00, protocol, 0,
)

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

private fun acHeaderUac1(streamingInterface: Int): ByteArray = descriptor(
    9, 0x24, 0x01,
    0x00, 0x01,
    0x28, 0x00,
    1,
    streamingInterface,
)

private fun uac2InputTerminal(): ByteArray = descriptor(
    17, 0x24, 0x02,
    1,
    0x01, 0x01,
    0,
    10,
    2,
    0x03, 0x00, 0x00, 0x00,
    0,
    0x00, 0x00,
    0,
)

private fun uac2OutputTerminal(): ByteArray = descriptor(
    12, 0x24, 0x03,
    2,
    0x01, 0x03,
    0,
    5,
    10,
    0x00, 0x00,
    0,
)

private fun uac2FeatureUnit(): ByteArray = descriptor(
    18, 0x24, 0x06,
    5,
    1,
    0x03, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0,
)

private fun uac2ClockSource(clockId: Int): ByteArray = descriptor(
    8, 0x24, 0x0A,
    clockId,
    0x01,
    0x03,
    0,
    0,
)

private fun uac2AsGeneral(channels: Int): ByteArray = descriptor(
    16, 0x24, 0x01,
    1,
    0x00,
    0x01,
    0x01, 0x00, 0x00, 0x00,
    channels,
    0x03, 0x00, 0x00, 0x00,
    0,
)

private fun uac2Format(subslot: Int, bits: Int): ByteArray = descriptor(
    6, 0x24, 0x02,
    0x01,
    subslot,
    bits,
)

private fun uac1InputTerminal(): ByteArray = descriptor(
    12, 0x24, 0x02,
    1,
    0x01, 0x01,
    0,
    2,
    0x03, 0x00,
    0,
    0,
)

private fun uac1OutputTerminal(): ByteArray = descriptor(
    9, 0x24, 0x03,
    2,
    0x01, 0x03,
    0,
    5,
    0,
)

private fun uac1FeatureUnit(): ByteArray = descriptor(
    10, 0x24, 0x06,
    5,
    1,
    1,
    0x03,
    0x00,
    0x00,
    0,
)

private fun uac1AsGeneral(terminalLink: Int): ByteArray = descriptor(
    7, 0x24, 0x01,
    terminalLink,
    0,
    0x01, 0x00,
)

private fun uac1Format(
    channels: Int,
    subslot: Int,
    bits: Int,
    rates: List<Int>,
): ByteArray {
    val header = descriptor(
        8 + rates.size * 3, 0x24, 0x02,
        0x01,
        channels,
        subslot,
        bits,
        rates.size,
    )
    return rates.fold(header) { acc, rate -> acc + le24(rate) }
}

private fun endpoint(
    address: Int,
    attributes: Int,
    maxPacket: Int,
    interval: Int,
    synchAddress: Int? = null,
): ByteArray {
    val base = descriptor(
        if (synchAddress == null) 7 else 9,
        0x05,
        address,
        attributes,
        maxPacket and 0xFF, (maxPacket shr 8) and 0xFF,
        interval,
    )
    return if (synchAddress == null) {
        base
    } else {
        base + descriptor(0, synchAddress)
    }
}

private fun classSpecificEndpoint(): ByteArray = descriptor(
    8, 0x25, 0x01,
    0x00,
    0x00,
    0x00,
    0x00, 0x00,
)

private fun descriptor(vararg values: Int): ByteArray =
    ByteArray(values.size) { values[it].toByte() }

private fun le24(value: Int): ByteArray = byteArrayOf(
    value.toByte(),
    (value shr 8).toByte(),
    (value shr 16).toByte(),
)

private fun bytes(vararg parts: ByteArray): ByteArray =
    parts.fold(ByteArray(0)) { acc, part -> acc + part }
