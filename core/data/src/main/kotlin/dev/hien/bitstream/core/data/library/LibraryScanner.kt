package dev.hien.bitstream.core.data.library

import dev.hien.bitstream.core.data.metadata.FlacMetadataReader
import dev.hien.bitstream.core.data.metadata.FlacTags
import dev.hien.bitstream.core.model.Track
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ScanResult(val tracks: List<Track>, val skipped: Int)

/**
 * Breadth-first SAF walk that reads only FLAC metadata. Track IDs are derived
 * from the document URI, so rescanning the same folder updates rows in place
 * instead of creating duplicates.
 */
class LibraryScanner(
    private val tree: FileTree,
    private val reader: FlacMetadataReader,
    private val storeArtwork: (suspend (key: String, bytes: ByteArray) -> String)? = null,
) {
    suspend fun scan(
        roots: List<FileNode>,
        onProgress: (Int) -> Unit = {},
    ): ScanResult = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        var skipped = 0

        val queue = ArrayDeque<Pair<FileNode, String>>()
        roots.forEach { queue.addLast(it to it.name) }

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (node, folder) = queue.removeFirst()

            if (node.isDirectory) {
                tree.children(node).forEach { child ->
                    queue.addLast(child to node.name)
                }
                continue
            }

            if (!node.name.endsWith(".flac", ignoreCase = true)) {
                skipped++
                continue
            }

            val tags = try {
                tree.open(node).use { reader.read(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                skipped++
                continue
            }

            tracks += tags.toTrack(node = node, folder = folder)
            onProgress(tracks.size)
        }

        ScanResult(tracks = tracks, skipped = skipped)
    }

    private suspend fun FlacTags.toTrack(node: FileNode, folder: String): Track {
        val title = vorbis.firstValue("TITLE") ?: node.name.substringBeforeLast('.')
        val artist = vorbis.firstValue("ARTIST").orEmpty()
        val album = vorbis.firstValue("ALBUM") ?: folder
        val albumArtist = vorbis.firstValue("ALBUMARTIST") ?: artist

        val artworkKey = picture?.let { sha256Hex(it) }
        val artworkUri = when {
            artworkKey == null -> null
            storeArtwork == null -> artworkKey
            else -> storeArtwork.invoke(artworkKey, picture)
        }

        return Track(
            id = stableId(node.uri),
            uri = node.uri,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            trackNumber = vorbis.firstValue("TRACKNUMBER").leadingNumber(),
            discNumber = vorbis.firstValue("DISCNUMBER").leadingNumber(),
            durationMs = streamInfo.durationMs,
            sampleRate = streamInfo.sampleRate,
            bitDepth = streamInfo.bitsPerSample,
            channels = streamInfo.channels,
            hasMd5 = streamInfo.md5 != null,
            artworkUri = artworkUri,
            year = vorbis.firstValue("DATE").year() ?: vorbis.firstValue("YEAR").year(),
            genre = vorbis.firstValue("GENRE").orEmpty(),
            folder = folder,
            size = node.size,
            modified = node.lastModified,
        )
    }

    companion object {
        /** Stable positive ID for a document URI, used as the tracks primary key. */
        fun stableId(uri: String): Long {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(uri.toByteArray(Charsets.UTF_8))
            var value = 0L
            for (index in 0 until 8) {
                value = (value shl 8) or (digest[index].toLong() and 0xFF)
            }
            value = value and Long.MAX_VALUE
            return if (value == 0L) 1L else value
        }

        internal fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}

private fun Map<String, List<String>>.firstValue(key: String): String? =
    this[key]?.firstOrNull()?.takeIf { it.isNotBlank() }

private fun String?.leadingNumber(): Int =
    this?.let { value -> Regex("\\d+").find(value)?.value?.toIntOrNull() } ?: 0

private fun String?.year(): Int? =
    this?.let { value -> Regex("\\d{4}").find(value)?.value?.toIntOrNull() }
