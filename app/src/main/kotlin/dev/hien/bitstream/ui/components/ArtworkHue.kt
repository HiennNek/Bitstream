package dev.hien.bitstream.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.hien.bitstream.core.designsystem.dominantArtworkHue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dominant hue of the playing artwork, decoded off the main thread. Null when
 * there is no artwork or the sleeve is monochrome, so the theme can fall back.
 */
@Composable
fun rememberArtworkHue(artworkUri: String?): Int? {
    val context = LocalContext.current
    var hue by remember(artworkUri) { mutableStateOf<Int?>(null) }
    LaunchedEffect(artworkUri) {
        hue = if (artworkUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { artworkHue(context, artworkUri) }.getOrNull()
            }
        }
    }
    return hue
}

private fun artworkHue(context: Context, artworkUri: String): Int? {
    val uri = Uri.parse(artworkUri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= SAMPLE_SIZE &&
        bounds.outHeight / (sample * 2) >= SAMPLE_SIZE
    ) {
        sample *= 2
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null

    return try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        dominantArtworkHue(pixels)
    } finally {
        bitmap.recycle()
    }
}

private const val SAMPLE_SIZE = 64
