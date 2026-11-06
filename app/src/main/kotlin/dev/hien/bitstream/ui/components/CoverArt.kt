package dev.hien.bitstream.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.hien.bitstream.core.designsystem.GeneratedCoverArt
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.trackHue
import dev.hien.bitstream.core.model.Track

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
 * Real artwork when the track has it, otherwise the mockup's generated blob.
 * Spins only when [spinning].
 */
@Composable
fun CoverArt(
    artworkUri: String?,
    seed: Long,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
    contentDescription: String? = null,
    hue: Int? = null,
) {
    val rotation = if (spinning) {
        val transition = rememberInfiniteTransition(label = "coverSpin")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(40_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "coverRotation",
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .testTag("cover_art")
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Radii.xxl)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
