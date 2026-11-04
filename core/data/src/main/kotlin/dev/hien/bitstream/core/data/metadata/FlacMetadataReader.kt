package dev.hien.bitstream.core.data.metadata

import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class StreamInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val totalSamples: Long,
    val md5: ByteArray?,
) {
    val durationMs: Long
        get() = if (sampleRate > 0) totalSamples * 1_000L / sampleRate else 0L
}

data class FlacTags(
    val streamInfo: StreamInfo,
    val vorbis: Map<String, List<String>>,
    val picture: ByteArray?,
)

/**
 * Parses only the FLAC metadata blocks, never the audio frames. Works on a
 * stream so callers can hand it a `ContentResolver` input stream without
 * reading the whole file.
 */
class FlacMetadataReader {
    fun read(input: InputStream): FlacTags {
        val stream = if (input.markSupported()) input else input.buffered()

        readMagic(stream)

        var streamInfo: StreamInfo? = null
        val vorbis = linkedMapOf<String, MutableList<String>>()
        var picture: ByteArray? = null
        var lastBlock = false

        while (!lastBlock) {
            val header = readFully(stream, 4)
            lastBlock = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)

            if (length > MAX_BLOCK_BYTES) {
                throw FlacFormatException("Metadata block of $length bytes exceeds the limit")
            }

            when (type) {
                BLOCK_STREAMINFO -> {
                    val payload = readFully(stream, length)
                    if (payload.size < 34) {
                        throw FlacFormatException("STREAMINFO block is too short")
                    }
                    streamInfo = parseStreamInfo(payload)
                }

                BLOCK_VORBIS_COMMENT -> parseVorbisComments(readFully(stream, length), vorbis)

                BLOCK_PICTURE -> {
                    val parsed = parsePicture(readFully(stream, length))
                    if (picture == null) picture = parsed.data
                    if (parsed.mime.isNotEmpty()) {
                        vorbis["COVERARTMIME"] = mutableListOf(parsed.mime)
                    }
                }

                else -> skipFully(stream, length.toLong())
            }
        }

        val info = streamInfo ?: throw FlacFormatException("FLAC stream has no STREAMINFO block")
        return FlacTags(
            streamInfo = info,
            vorbis = vorbis.mapValues { (_, values) -> values.toList() },
            picture = picture,
        )
    }

    private fun readMagic(stream: InputStream) {
        val first = readFully(stream, 3)

        if (!first.contentEquals(ID3_MAGIC)) {
            val rest = readFully(stream, 1)
            val magic = first + rest
            if (!magic.contentEquals(FLAC_MAGIC)) {
                throw FlacFormatException("Not a FLAC stream")
            }
            return
        }

        // Skip the rest of the ID3v2 header, the tag body, and an optional footer.
        val header = readFully(stream, 7)
        val flags = header[2].toInt() and 0xFF
        val tagSize = syncSafeInt(header, 3)
        skipFully(stream, tagSize.toLong())
        if (flags and ID3_FOOTER_FLAG != 0) {
            skipFully(stream, 10)
        }

        val magic = readFully(stream, 4)
        if (!magic.contentEquals(FLAC_MAGIC)) {
            throw FlacFormatException("ID3-prefixed data is not a FLAC stream")
        }
    }

    private fun parseStreamInfo(payload: ByteArray): StreamInfo {
        val packed = (0 until 8).fold(0L) { value, index ->
            (value shl 8) or (payload[10 + index].toLong() and 0xFF)
        }

        val sampleRate = (packed ushr 44).toInt() and 0xFFFFF
        val channels = ((packed ushr 41).toInt() and 0x7) + 1
        val bitsPerSample = ((packed ushr 36).toInt() and 0x1F) + 1
        val totalSamples = packed and 0xFFFFFFFFFL

        val md5 = payload.copyOfRange(18, 34)
        return StreamInfo(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            totalSamples = totalSamples,
            md5 = if (md5.all { it == 0.toByte() }) null else md5,
        )
    }

    private fun parseVorbisComments(payload: ByteArray, into: MutableMap<String, MutableList<String>>) {
        var offset = 0

        val vendorLength = le32(payload, offset)
        offset += 4
        if (vendorLength < 0 || offset + vendorLength > payload.size) {
            throw FlacFormatException("Malformed Vorbis vendor string")
        }
        offset += vendorLength

        val count = le32(payload, offset)
        offset += 4
        if (count < 0) throw FlacFormatException("Malformed Vorbis comment count")

        repeat(count) {
            if (offset + 4 > payload.size) throw FlacFormatException("Truncated Vorbis comment")
            val length = le32(payload, offset)
            offset += 4
            if (length < 0 || offset + length > payload.size) {
                throw FlacFormatException("Truncated Vorbis comment")
            }

            val entry = String(payload, offset, length, StandardCharsets.UTF_8)
            offset += length

            val separator = entry.indexOf('=')
            if (separator > 0) {
                val key = entry.substring(0, separator).uppercase()
                val value = entry.substring(separator + 1)
                into.getOrPut(key) { mutableListOf() } += value
            }
        }
    }

    private fun parsePicture(payload: ByteArray): Picture {
        var offset = 0

        offset += 4 // picture type
        val mimeLength = be32(payload, offset)
        offset += 4
        if (mimeLength < 0 || offset + mimeLength > payload.size) {
            throw FlacFormatException("Malformed PICTURE MIME type")
        }
        val mime = String(payload, offset, mimeLength, StandardCharsets.US_ASCII)
        offset += mimeLength

        val descriptionLength = be32(payload, offset)
        offset += 4
        if (descriptionLength < 0 || offset + descriptionLength > payload.size) {
            throw FlacFormatException("Malformed PICTURE description")
        }
        offset += descriptionLength

        offset += 16 // width, height, color depth, colors used

        val dataLength = be32(payload, offset)
        offset += 4
        if (dataLength < 0 || offset + dataLength > payload.size) {
            throw FlacFormatException("Malformed PICTURE data")
        }

        return Picture(mime = mime, data = payload.copyOfRange(offset, offset + dataLength))
    }

    private fun be32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) throw FlacFormatException("Truncated metadata field")
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun le32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) throw FlacFormatException("Truncated metadata field")
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun syncSafeInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) throw FlacFormatException("Truncated ID3 header")
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun readFully(stream: InputStream, count: Int): ByteArray {
        if (count == 0) return ByteArray(0)
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = stream.read(buffer, read, count - read)
            if (n < 0) throw EOFException("Unexpected end of FLAC metadata")
            read += n
        }
        return buffer
    }

    private fun skipFully(stream: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            if (stream.read() < 0) throw EOFException("Unexpected end of FLAC metadata")
            remaining--
        }
    }

    private data class Picture(val mime: String, val data: ByteArray)

    companion object {
        private const val MAX_BLOCK_BYTES = 16 * 1024 * 1024

        private const val BLOCK_STREAMINFO = 0
        private const val BLOCK_VORBIS_COMMENT = 4
        private const val BLOCK_PICTURE = 6

        private const val ID3_FOOTER_FLAG = 0x10

        private val FLAC_MAGIC = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
        private val ID3_MAGIC = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())
    }
}

class FlacFormatException(message: String) : Exception(message)
