package dev.hien.bitstream.core.data.library

import dev.hien.bitstream.core.data.metadata.FlacMetadataReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScannerTest {
    @Test
    fun indexesFlacFilesAndIgnoresOtherExtensions() = runTest {
        val tree = FakeFileTree(
            root = dir("Music"),
            files = mapOf(
                "Music/a.flac" to flacBytes(title = "A"),
                "Music/b.FLAC" to flacBytes(title = "B"),
                "Music/cover.jpg" to byteArrayOf(1, 2, 3),
            ),
        )

        val result = LibraryScanner(tree, FlacMetadataReader()).scan(listOf(tree.root))

        assertEquals(setOf("A", "B"), result.tracks.map { it.title }.toSet())
        assertEquals(1, result.skipped)
    }

    @Test
    fun scannerKeepsExactSourceFormat() = runTest {
        val tree = FakeFileTree(
            root = dir("Music"),
            files = mapOf("Music/hires.flac" to flacBytes(rate = 192_000, depth = 24)),
        )

        val track = LibraryScanner(tree, FlacMetadataReader())
            .scan(listOf(tree.root))
            .tracks
            .single()

        assertEquals(192_000, track.sampleRate)
        assertEquals(24, track.bitDepth)
        assertTrue(track.hasMd5)
    }

    @Test
    fun scannerDerivesFallbacksAndFolder() = runTest {
        val tree = FakeFileTree(
            root = dir("Music"),
            files = mapOf("Music/Album/01 - Untitled.flac" to flacBytes(title = null)),
        )

        val track = LibraryScanner(tree, FlacMetadataReader())
            .scan(listOf(tree.root))
            .tracks
            .single()

        assertEquals("01 - Untitled", track.title)
        assertEquals("Album", track.album)
        assertEquals("Album", track.folder)
    }

    @Test
    fun scannerReportsProgressAndStableIds() = runTest {
        val tree = FakeFileTree(
            root = dir("Music"),
            files = mapOf(
                "Music/one.flac" to flacBytes(title = "One"),
                "Music/two.flac" to flacBytes(title = "Two"),
            ),
        )
        val progress = mutableListOf<Int>()
        val scanner = LibraryScanner(tree, FlacMetadataReader())

        val first = scanner.scan(listOf(tree.root)) { progress += it }
        val second = scanner.scan(listOf(tree.root))

        assertEquals(listOf(1, 2), progress)
        assertEquals(
            first.tracks.map { it.id }.toSet(),
            second.tracks.map { it.id }.toSet(),
        )
        assertFalse(first.tracks.first().id == 0L)
    }
}

private class FakeNode(
    override val name: String,
    override val uri: String,
    override val size: Long = 0,
    override val lastModified: Long = 0,
    override val isDirectory: Boolean,
) : FileNode {
    val children = mutableListOf<FakeNode>()
}

private class FakeFileTree(
    val root: FakeNode,
    private val files: Map<String, ByteArray>,
) : FileTree {
    private val nodesByUri = mutableMapOf(root.uri to root)

    init {
        files.keys.forEach { path ->
            val parts = path.split('/')
            var parent = root
            for (part in parts.drop(1).dropLast(1)) {
                val parentPath = parent.uri.removePrefix("content://test/")
                val uri = "content://test/$parentPath/$part"
                parent = nodesByUri.getOrPut(uri) {
                    FakeNode(name = part, uri = uri, isDirectory = true).also { parent.children += it }
                }
            }
            val fileName = parts.last()
            val uri = "content://test/$path"
            val node = FakeNode(
                name = fileName,
                uri = uri,
                size = files.getValue(path).size.toLong(),
                isDirectory = false,
            )
            nodesByUri[uri] = node
            parent.children += node
        }
    }

    override fun children(node: FileNode): List<FileNode> = (node as FakeNode).children

    override fun open(node: FileNode): InputStream =
        files.getValue(node.uri.removePrefix("content://test/")).inputStream()
}

private fun dir(name: String): FakeNode =
    FakeNode(name = name, uri = "content://test/$name", isDirectory = true)

private fun flacBytes(
    title: String? = "Track",
    rate: Int = 44_100,
    depth: Int = 16,
): ByteArray {
    val out = ByteArrayOutputStream()
    out.write("fLaC".toByteArray(Charsets.US_ASCII))

    val streamInfo = ByteArrayOutputStream()
    streamInfo.writeBe16(4096)
    streamInfo.writeBe16(4096)
    streamInfo.writeBe24(0)
    streamInfo.writeBe24(0)
    val totalSamples = rate.toLong() * 3
    val packed = (rate.toLong() shl 44) or
        ((2 - 1).toLong() shl 41) or
        ((depth - 1).toLong() shl 36) or
        totalSamples
    repeat(8) { streamInfo.write((packed shr (56 - it * 8)).toInt() and 0xFF) }
    streamInfo.write(ByteArray(16) { (it + 1).toByte() })
    out.writeBlock(type = 0, payload = streamInfo.toByteArray(), last = false)

    val vorbis = ByteArrayOutputStream()
    val vendor = "test".toByteArray()
    vorbis.writeLe32(vendor.size)
    vorbis.write(vendor)
    val comments = buildList {
        if (title != null) add("TITLE=$title")
        add("ARTIST=Coltrane")
        add("ALBUMARTIST=Coltrane")
        add("TRACKNUMBER=1")
    }
    vorbis.writeLe32(comments.size)
    comments.forEach {
        val bytes = it.toByteArray()
        vorbis.writeLe32(bytes.size)
        vorbis.write(bytes)
    }
    out.writeBlock(type = 4, payload = vorbis.toByteArray(), last = true)

    return out.toByteArray()
}

private fun ByteArrayOutputStream.writeBlock(type: Int, payload: ByteArray, last: Boolean) {
    write((if (last) 0x80 else 0) or type)
    write((payload.size shr 16) and 0xFF)
    write((payload.size shr 8) and 0xFF)
    write(payload.size and 0xFF)
    write(payload)
}

private fun ByteArrayOutputStream.writeBe16(value: Int) {
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeBe24(value: Int) {
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
