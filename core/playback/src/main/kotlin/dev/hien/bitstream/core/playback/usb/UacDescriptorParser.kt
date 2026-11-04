package dev.hien.bitstream.core.playback.usb

/**
 * Parses the raw configuration descriptor of a USB Audio Class 1 or 2 device.
 *
 * The parser is deliberately defensive: a malformed or truncated descriptor
 * returns `null` instead of throwing, because the bytes come from hardware and
 * a broken DAC must not crash playback setup.
 */
class UacDescriptorParser {
    fun parse(raw: ByteArray): UacDevice? {
        if (raw.size < 2) return null

        var offset = 0
        var uacVersion: Int? = null
        var controlInterface: Int? = null
        var clockSourceId: Int? = null
        var featureUnitId: Int? = null
        var volume: VolumeRange? = null

        var current: InterfaceInfo? = null
        var currentAlt: AltSetting? = null
        val candidates = mutableListOf<AltSetting>()

        while (offset < raw.size) {
            if (offset + 2 > raw.size) return null

            val length = raw[offset].u8()
            if (length < 2 || offset + length > raw.size) return null
            val type = raw[offset + 1].u8()

            when (type) {
                DESCRIPTOR_INTERFACE -> {
                    if (length < 9) return null
                    currentAlt?.takeIf { it.isStreaming }?.let(candidates::add)

                    val info = InterfaceInfo(
                        number = raw[offset + 2].u8(),
                        alt = raw[offset + 3].u8(),
                        classCode = raw[offset + 5].u8(),
                        subclass = raw[offset + 6].u8(),
                        protocol = raw[offset + 7].u8(),
                    )
                    current = info
                    currentAlt = AltSetting(
                        interfaceNumber = info.number,
                        altSetting = info.alt,
                        protocol = info.protocol,
                        isStreaming = info.classCode == CLASS_AUDIO &&
                            info.subclass == SUBCLASS_STREAMING,
                    )

                    if (info.classCode == CLASS_AUDIO && info.subclass == SUBCLASS_CONTROL) {
                        if (controlInterface == null) controlInterface = info.number
                    }
                }

                DESCRIPTOR_ENDPOINT -> {
                    if (length < 7) return null
                    val alt = currentAlt
                    if (alt != null && alt.isStreaming) {
                        val address = raw[offset + 2].u8()
                        val attributes = raw[offset + 3].u8()
                        val transferType = attributes and 0x03
                        val directionIn = (address and 0x80) != 0
                        val usage = (attributes shr 4) and 0x03
                        val sync = (attributes shr 2) and 0x03

                        if (transferType == TRANSFER_ISO && !directionIn && usage == USAGE_DATA) {
                            alt.dataEndpoint = Endpoint(
                                address = address,
                                syncType = syncTypeOf(sync),
                                maxPacketSize = le16(raw, offset + 4),
                                interval = raw[offset + 6].u8(),
                                // UAC1 puts the feedback address in bSynchAddress.
                                feedbackAddress = if (length >= 9) {
                                    raw[offset + 8].u8().takeIf { it != 0 }
                                } else {
                                    null
                                },
                            )
                        } else if (
                            transferType == TRANSFER_ISO &&
                            directionIn &&
                            usage == USAGE_FEEDBACK
                        ) {
                            alt.feedbackEndpoint = address
                        }
                    }
                }

                DESCRIPTOR_CS_INTERFACE -> {
                    if (length >= 3) {
                        val info = current
                        val subtype = raw[offset + 2].u8()
                        if (info != null && info.classCode == CLASS_AUDIO) {
                            if (info.subclass == SUBCLASS_CONTROL) {
                                when (subtype) {
                                    AC_HEADER -> {
                                        if (length >= 5) {
                                            val version = le16(raw, offset + 3) ushr 8
                                            if (version == 1 || version == 2) uacVersion = version
                                        }
                                    }

                                    AC_CLOCK_SOURCE -> {
                                        if (length >= 4 && clockSourceId == null) {
                                            clockSourceId = raw[offset + 3].u8()
                                        }
                                    }

                                    AC_FEATURE_UNIT -> {
                                        val parsed = parseFeatureUnit(
                                            raw = raw,
                                            offset = offset,
                                            length = length,
                                            version = uacVersion ?: protocolVersion(info.protocol),
                                        )
                                        if (parsed != null && parsed.hasVolume) {
                                            featureUnitId = parsed.unitId
                                            if (parsed.volume != null) volume = parsed.volume
                                        }
                                    }
                                }
                            } else if (info.subclass == SUBCLASS_STREAMING) {
                                val alt = currentAlt
                                if (alt != null) {
                                    when (subtype) {
                                        AS_GENERAL -> {
                                            if (length >= 16) {
                                                alt.channels = raw[offset + 10].u8()
                                            }
                                        }

                                        AS_FORMAT_TYPE -> {
                                            parseFormat(
                                                raw = raw,
                                                offset = offset,
                                                length = length,
                                                asChannels = alt.channels,
                                                version = uacVersion ?: protocolVersion(info.protocol),
                                            )?.let(alt.formats::add)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            offset += length
        }

        currentAlt?.takeIf { it.isStreaming }?.let(candidates::add)

        val chosen = candidates
            .filter { it.dataEndpoint != null && it.formats.isNotEmpty() }
            .maxWithOrNull(
                compareBy<AltSetting>(
                    // This app is stereo-only, so prefer a stereo alternate
                    // setting when the device exposes other channel counts.
                    { if (it.formats.any { format -> format.channels == 2 }) 1 else 0 },
                    { it.formats.maxOfOrNull { format -> format.bitResolution } ?: 0 },
                    { it.formats.maxOfOrNull { format -> format.subslotBytes } ?: 0 },
                    { it.altSetting },
                ),
            )
            ?: return null

        val endpoint = chosen.dataEndpoint ?: return null
        val version = uacVersion ?: protocolVersion(chosen.protocol)

        return UacDevice(
            uacVersion = version,
            controlInterface = controlInterface ?: return null,
            streamingInterface = chosen.interfaceNumber,
            altSetting = chosen.altSetting,
            endpointAddress = endpoint.address,
            syncType = endpoint.syncType,
            feedbackEndpoint = endpoint.feedbackAddress ?: chosen.feedbackEndpoint,
            formats = chosen.formats.toList(),
            clockSourceId = clockSourceId,
            featureUnitId = featureUnitId,
            volume = volume,
            rawDescriptor = raw.copyOf(),
            maxPacketSize = endpoint.maxPacketSize,
            interval = endpoint.interval,
        )
    }

    private fun parseFormat(
        raw: ByteArray,
        offset: Int,
        length: Int,
        asChannels: Int,
        version: Int,
    ): UacFormat? {
        if (length < 6) return null
        if (raw[offset + 3].u8() != FORMAT_TYPE_I) return null

        // UAC1 Type-I format descriptor, and the optional vendor extension where
        // a UAC2 device appends its rate table to the format descriptor.
        if (length >= 8) {
            val channels = raw[offset + 4].u8()
            val subslot = raw[offset + 5].u8()
            val bits = raw[offset + 6].u8()
            val rateType = raw[offset + 7].u8()
            if (channels !in 1..32 || subslot !in 1..4 || bits !in 1..32) return null

            val rates = mutableListOf<Int>()
            if (rateType > 0) {
                if (length < 8 + rateType * 3) return null
                repeat(rateType) { index ->
                    val rate = le24(raw, offset + 8 + index * 3)
                    if (rate > 0) rates += rate
                }
            }
            return UacFormat(
                channels = channels,
                subslotBytes = subslot,
                bitResolution = bits,
                sampleRates = rates.distinct(),
            )
        }

        // Standard UAC2 Type-I format descriptor. The channel count comes from
        // the AS General descriptor, and rates are queried from the clock source
        // at runtime rather than read from the descriptor.
        if (length == 6 && version == 2) {
            val subslot = raw[offset + 4].u8()
            val bits = raw[offset + 5].u8()
            if (subslot !in 1..4 || bits !in 1..32 || asChannels !in 1..32) return null
            return UacFormat(
                channels = asChannels,
                subslotBytes = subslot,
                bitResolution = bits,
                sampleRates = emptyList(),
            )
        }

        return null
    }

    private fun parseFeatureUnit(
        raw: ByteArray,
        offset: Int,
        length: Int,
        version: Int,
    ): FeatureUnit? {
        if (length < 6) return null
        val unitId = raw[offset + 3].u8()
        val hasVolume: Boolean

        if (version == 2) {
            // bLength = 6 + 4 * (channels + 1); the master control is first.
            val entries = (length - 6) / 4
            if (entries <= 0) return null
            hasVolume = (0 until entries).any { index ->
                (le32(raw, offset + 5 + index * 4) and VOLUME_CONTROL) != 0
            }
        } else {
            // bLength = 7 + controlSize * (channels + 1); controls start at 6.
            val controlSize = raw[offset + 5].u8()
            if (controlSize !in 1..4) return null
            val entries = (length - 7) / controlSize
            if (entries <= 0) return null
            hasVolume = (0 until entries).any { index ->
                (readLittleEndian(raw, offset + 6 + index * controlSize, controlSize) and
                    VOLUME_CONTROL) != 0
            }
        }

        return FeatureUnit(unitId = unitId, hasVolume = hasVolume)
    }

    private fun syncTypeOf(value: Int): SyncType = when (value) {
        1 -> SyncType.ASYNC
        2 -> SyncType.ADAPTIVE
        3 -> SyncType.SYNC
        else -> SyncType.NONE
    }

    private fun protocolVersion(protocol: Int): Int =
        if (protocol == PROTOCOL_UAC2) 2 else 1

    private fun le16(bytes: ByteArray, offset: Int): Int =
        bytes[offset].u8() or (bytes[offset + 1].u8() shl 8)

    private fun le24(bytes: ByteArray, offset: Int): Int =
        bytes[offset].u8() or
            (bytes[offset + 1].u8() shl 8) or
            (bytes[offset + 2].u8() shl 16)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        bytes[offset].u8() or
            (bytes[offset + 1].u8() shl 8) or
            (bytes[offset + 2].u8() shl 16) or
            (bytes[offset + 3].u8() shl 24)

    private fun readLittleEndian(bytes: ByteArray, offset: Int, size: Int): Int {
        var value = 0
        for (index in 0 until size) {
            value = value or (bytes[offset + index].u8() shl (index * 8))
        }
        return value
    }

    private fun Byte.u8(): Int = toInt() and 0xFF

    private data class InterfaceInfo(
        val number: Int,
        val alt: Int,
        val classCode: Int,
        val subclass: Int,
        val protocol: Int,
    )

    private data class AltSetting(
        val interfaceNumber: Int,
        val altSetting: Int,
        val protocol: Int,
        val isStreaming: Boolean,
    ) {
        var channels: Int = 0
        var dataEndpoint: Endpoint? = null
        var feedbackEndpoint: Int? = null
        val formats = mutableListOf<UacFormat>()
    }

    private data class Endpoint(
        val address: Int,
        val syncType: SyncType,
        val maxPacketSize: Int,
        val interval: Int,
        val feedbackAddress: Int?,
    )

    private data class FeatureUnit(
        val unitId: Int,
        val hasVolume: Boolean,
        val volume: VolumeRange? = null,
    )

    private companion object {
        const val DESCRIPTOR_INTERFACE = 0x04
        const val DESCRIPTOR_ENDPOINT = 0x05
        const val DESCRIPTOR_CS_INTERFACE = 0x24

        const val CLASS_AUDIO = 0x01
        const val SUBCLASS_CONTROL = 0x01
        const val SUBCLASS_STREAMING = 0x02
        const val PROTOCOL_UAC2 = 0x20

        const val AC_HEADER = 0x01
        const val AC_FEATURE_UNIT = 0x06
        const val AC_CLOCK_SOURCE = 0x0A

        const val AS_GENERAL = 0x01
        const val AS_FORMAT_TYPE = 0x02

        const val FORMAT_TYPE_I = 0x01
        const val TRANSFER_ISO = 0x01
        const val USAGE_DATA = 0x00
        const val USAGE_FEEDBACK = 0x01

        /** Feature-unit control bit D1. */
        const val VOLUME_CONTROL = 0x02
    }
}
