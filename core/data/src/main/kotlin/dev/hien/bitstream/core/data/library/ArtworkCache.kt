package dev.hien.bitstream.core.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * File-backed artwork cache keyed by the content hash of the embedded picture.
 * Images are downscaled once to at most [maxDimension] pixels and stored as
 * JPEG; the cache is trimmed oldest-first to [maxBytes].
 */
class ArtworkCache(
    context: Context,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxDimension: Int = DEFAULT_MAX_DIMENSION,
) {
    private val directory = File(context.cacheDir, "artwork")
    private val mutex = Mutex()

    suspend fun store(key: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.mkdirs()
            val file = File(directory, "${safeName(key)}.img")

            if (!file.isFile || file.length() == 0L) {
                val encoded = downscale(bytes)
                val temp = File(directory, "${file.name}.tmp")
                temp.writeBytes(encoded)
                if (!temp.renameTo(file)) {
                    temp.delete()
                    throw IllegalStateException("Could not cache artwork for $key")
                }
            }

            file.setLastModified(System.currentTimeMillis())
            trim()
            Uri.fromFile(file).toString()
        }
    }

    fun file(key: String): File? =
        File(directory, "${safeName(key)}.img").takeIf { it.isFile }

    private fun downscale(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension ||
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes

        return bitmap.use {
            val out = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun trim() {
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return
            total -= file.length()
            file.delete()
        }
    }

    private fun safeName(key: String): String {
        val cleaned = key.map { character ->
            if (character.isLetterOrDigit() || character in "-_.") character else '_'
        }.joinToString("")
        return cleaned.ifBlank { "artwork" }
    }

    private inline fun <T> Bitmap.use(block: (Bitmap) -> T): T =
        try {
            block(this)
        } finally {
            recycle()
        }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 256L * 1024 * 1024
        const val DEFAULT_MAX_DIMENSION: Int = 1024
        private const val JPEG_QUALITY = 90
    }
}
