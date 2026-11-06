package dev.hien.bitstream.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.core.designsystem.GeneratedCoverArt
import dev.hien.bitstream.core.designsystem.rememberMotionEnabled
import dev.hien.bitstream.core.designsystem.trackHue
import dev.hien.bitstream.core.model.Track

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val cookies = listOf(
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie12Sided,
)

/** Deterministically picks the scalloped cover shape for a track. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun cookieFor(seed: Long): RoundedPolygon =
    cookies[Math.floorMod(seed, cookies.size.toLong()).toInt()]

@Composable
fun CoverArt(
    track: Track,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
    contentDescription: String? = null,
) {
    CoverArt(
        artworkUri = track.artworkUri,
        seed = track.id,
        modifier = modifier,
        spinning = spinning,
        contentDescription = contentDescription,
        hue = trackHue(track.albumArtist, track.album, track.artist, track.title),
    )
}

/**
 * Real artwork when the track has it, otherwise a generated cookie placeholder.
 * The cookie rotates only while [spinning] and when system animations are on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoverArt(
    artworkUri: String?,
    seed: Long,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
    contentDescription: String? = null,
    hue: Int? = null,
) {
    val motionEnabled = rememberMotionEnabled()
    // Keep the angle across pauses: cancelling animateTo freezes it where it is.
    val angle = remember { Animatable(0f) }
    LaunchedEffect(spinning, motionEnabled) {
        if (!spinning || !motionEnabled) return@LaunchedEffect
        while (true) {
            angle.animateTo(
                targetValue = angle.value + 360f,
                animationSpec = tween(40_000, easing = LinearEasing),
            )
        }
    }
    CookieCover(
        artworkUri = artworkUri,
        seed = seed,
        rotation = angle.value,
        contentDescription = contentDescription,
        hue = hue,
        modifier = modifier,
    )
}

/**
 * The shape clip lives on the same layer as the rotation, so the mask turns
 * with the artwork. The shape is drawn at 90% of the box to leave rotation
 * headroom inside parent scroll and pane bounds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieCover(
    artworkUri: String?,
    seed: Long,
    rotation: Float,
    contentDescription: String?,
    hue: Int?,
    modifier: Modifier = Modifier,
) {
    val shape: Shape = remember(seed) { cookieFor(seed) }.toShape()

    Box(
        modifier = modifier
            .testTag("cover_art")
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .graphicsLayer {
                    rotationZ = rotation
                    this.shape = shape
                    clip = true
                },
        ) {
            if (!artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                val descriptionModifier = if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                }
                GeneratedCoverArt(
                    hue = hue ?: Math.floorMod(seed, 360L).toInt(),
                    seed = seed,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(descriptionModifier),
                )
            }
        }
    }
}

@Preview(name = "Cover art 290dp light", widthDp = 1300, heightDp = 340)
@Composable
private fun CoverArtLightPreview() = CoverArtAngleGrid(dark = false, size = 290.dp)

@Preview(name = "Cover art 290dp dark", widthDp = 1300, heightDp = 340)
@Composable
private fun CoverArtDarkPreview() = CoverArtAngleGrid(dark = true, size = 290.dp)

@Preview(name = "Cover art 40dp light", widthDp = 260, heightDp = 80)
@Composable
private fun CoverArtSmallLightPreview() = CoverArtAngleGrid(dark = false, size = 40.dp)

@Preview(name = "Cover art 40dp dark", widthDp = 260, heightDp = 80)
@Composable
private fun CoverArtSmallDarkPreview() = CoverArtAngleGrid(dark = true, size = 40.dp)

@Composable
internal fun CoverArtAngleGrid(dark: Boolean, size: Dp) {
    BitstreamTheme(darkTheme = dark, albumColors = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                listOf(0f, 15f, 30f, 45f).forEach { angle ->
                    CookieCover(
                        artworkUri = null,
                        seed = 1L,
                        rotation = angle,
                        contentDescription = null,
                        hue = 12,
                        modifier = Modifier.size(size),
                    )
                }
            }
        }
    }
}
