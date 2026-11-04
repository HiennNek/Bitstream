package dev.hien.bitstream.core.data.metadata

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlacMetadataReaderTest {
    @Test
    fun readsStreamInfoAndVorbisComments() {
        val tags = FlacMetadataReader().read(flacBytes().inputStream())

        assertEquals(96_000, tags.streamInfo.sampleRate)
        assertEquals(24, tags.streamInfo.bitsPerSample)
        assertEquals(2, tags.streamInfo.channels)
        assertEquals(960_000L, tags.streamInfo.totalSamples)
        assertEquals(listOf("Coltrane"), tags.vorbis["ARTIST"])
        assertEquals(listOf("Blue Train"), tags.vorbis["TITLE"])
        assertEquals(listOf("Blue Train"), tags.vorbis["ALBUM"])
        assertArrayEquals(ByteArray(16) { (it + 1).toByte() }, tags.streamInfo.md5)
    }

    @Test
    fun readsId3v2PrefixedFlac() {
        val tags = FlacMetadataReader().read(flacBytes(id3Prefix = true).inputStream())

        assertEquals(96_000, tags.streamInfo.sampleRate)
        assertEquals(listOf("Coltrane"), tags.vorbis["ARTIST"])
    }

    @Test
    fun allZeroMd5YieldsNull() {
        val tags = FlacMetadataReader().read(
            flacBytes(md5 = ByteArray(16)).inputStream(),
        )

        assertNull(tags.streamInfo.md5)
    }

    @Test
    fun readsPictureBlockAndItsMimeType() {
        val artwork = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
        val tags = FlacMetadataReader().read(
            flacBytes(picture = artwork, pictureMime = "image/png").inputStream(),
        )

        assertArrayEquals(artwork, tags.picture)
        assertEquals(listOf("image/png"), tags.vorbis["COVERARTMIME"])
    }

    @Test
    fun omitsCoverArtMimeWhenThereIsNoPicture() {
        val tags = FlacMetadataReader().read(flacBytes().inputStream())

        assertNull(tags.picture)
        assertNull(tags.vorbis["COVERARTMIME"])
    }
}

private fun flacBytes(
    sampleRate: Int = 96_000,
    channels: Int = 2,
    bitsPerSample: Int = 24,
    md5: ByteArray = ByteArray(16) { (it + 1).toByte() },
    id3Prefix: Boolean = false,
    picture: ByteArray? = null,
    pictureMime: String = "image/png",
): ByteArray {
    val out = ByteArrayOutputStream()

    if (id3Prefix) {
        // ID3v2.3 header with an empty 10-byte tag body; size is sync-safe.
        out.write("ID3".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x00, 0x00))
        out.write(syncSafe(10))
        out.write(ByteArray(10))
    }

    out.write("fLaC".toByteArray(Charsets.US_ASCII))

    val blocks = mutableListOf<Pair<Int, ByteArray>>()
    blocks += 0 to streamInfoBlock(sampleRate, channels, bitsPerSample, md5)
    blocks += 4 to vorbisBlock(
        listOf(
            "TITLE" to "Blue Train",
            "ARTIST" to "Coltrane",
            "ALBUM" to "Blue Train",
        ),
    )
    if (picture != null) {
        blocks += 6 to pictureBlock(picture, pictureMime)
    }

    blocks.forEachIndexed { index, (type, payload) ->
        val isLast = index == blocks.lastIndex
        out.write(blockHeader(type = type, length = payload.size, last = isLast))
        out.write(payload)
    }

    return out.toByteArray()
}

private fun streamInfoBlock(
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
    md5: ByteArray,
): ByteArray {
    val out = ByteArrayOutputStream()
    out.writeBe16(4096)
    out.writeBe16(4096)
    out.writeBe24(0)
    out.writeBe24(0)

    val totalSamples = sampleRate * 10L
    val packed = (sampleRate.toLong() shl 44) or
        ((channels - 1).toLong() shl 41) or
        ((bitsPerSample - 1).toLong() shl 36) or
        totalSamples
    repeat(8) { shift -> out.write((packed shr (56 - shift * 8)).toInt() and 0xFF) }

    out.write(md5)
    return out.toByteArray()
}

private fun vorbisBlock(comments: List<Pair<String, String>>): ByteArray {
    val out = ByteArrayOutputStream()
    val vendor = "BitstreamTest".toByteArray(Charsets.UTF_8)
    out.writeLe32(vendor.size)
    out.write(vendor)
    out.writeLe32(comments.size)
    comments.forEach { (key, value) ->
        val entry = "$key=$value".toByteArray(Charsets.UTF_8)
        out.writeLe32(entry.size)
        out.write(entry)
    }
    return out.toByteArray()
}

private fun pictureBlock(data: ByteArray, mime: String): ByteArray {
    val out = ByteArrayOutputStream()
    val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
    out.writeBe32(3) // front cover
    out.writeBe32(mimeBytes.size)
    out.write(mimeBytes)
    out.writeBe32(0) // empty description
    out.writeBe32(1) // width
    out.writeBe32(1) // height
    out.writeBe32(24) // color depth
    out.writeBe32(0) // colors used
    out.writeBe32(data.size)
    out.write(data)
    return out.toByteArray()
}

private fun blockHeader(type: Int, length: Int, last: Boolean): ByteArray =
    byteArrayOf(
        ((if (last) 0x80 else 0) or type).toByte(),
        (length shr 16).toByte(),
        (length shr 8).toByte(),
        length.toByte(),
    )

private fun syncSafe(value: Int): ByteArray = byteArrayOf(
    ((value shr 21) and 0x7F).toByte(),
    ((value shr 14) and 0x7F).toByte(),
    ((value shr 7) and 0x7F).toByte(),
    (value and 0x7F).toByte(),
)

private fun ByteArrayOutputStream.writeBe16(value: Int) {
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeBe24(value: Int) {
    write((value shr 16) and 0xFF)
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeBe32(value: Int) {
    write((value shr 24) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeLe32(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}
