package dev.hien.bitstream.ui.components

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import dev.hien.bitstream.core.model.Track
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val outerShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Flower,
    MaterialShapes.Puffy,
    MaterialShapes.Burst,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val innerShapes = listOf(
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Sunny,
    MaterialShapes.PuffyDiamond,
)

/** Deterministically picks the outer blob for a track. */
fun coverShape(seed: Long): RoundedPolygon =
    outerShapes[Math.floorMod(seed, outerShapes.size.toLong()).toInt()]

/** Deterministically picks the inner blob for a track. */
fun innerCoverShape(seed: Long): RoundedPolygon =
    innerShapes[Math.floorMod(seed, innerShapes.size.toLong()).toInt()]

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
    )
}

/**
 * Real artwork when the track has it, otherwise a generated blob built from
 * [androidx.compose.material3.MaterialShapes]. Spins only when [spinning].
 */
@Composable
fun CoverArt(
    artworkUri: String?,
    seed: Long,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
    contentDescription: String? = null,
) {
    val outer = remember(seed) { coverShape(seed) }
    val inner = remember(seed) { innerCoverShape(seed) }
    val shape = remember(outer) { outer.toShape() }
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
                    .clip(shape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            val primary = MaterialTheme.colorScheme.primary
            val innerColor = MaterialTheme.colorScheme.tertiaryContainer
            val descriptionModifier = if (contentDescription == null) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(descriptionModifier),
            ) {
                drawFitted(outer, primary, scaleFactor = 1f)
                drawFitted(inner, innerColor, scaleFactor = 0.62f)
                drawCircle(
                    color = primary,
                    radius = size.minDimension * 0.09f,
                    center = center,
                )
            }
        }
    }
}

private fun DrawScope.drawFitted(polygon: RoundedPolygon, color: Color, scaleFactor: Float) {
    val path = polygon.toPath().asComposePath()
    val bounds = path.getBounds()
    if (bounds.width <= 0f || bounds.height <= 0f) return
    val factor = min(size.width / bounds.width, size.height / bounds.height) * scaleFactor
    translate(
        left = size.width / 2f - bounds.center.x * factor,
        top = size.height / 2f - bounds.center.y * factor,
    ) {
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

private fun RoundedPolygon.toShape(): Shape = GenericShape { size, _ ->
    addPath(toFitPath(size))
}

private fun RoundedPolygon.toFitPath(size: Size): Path {
    val path = toPath()
    val bounds = RectF()
    path.computeBounds(bounds, true)
    if (bounds.width() <= 0f || bounds.height() <= 0f) return path.asComposePath()
    val factor = min(size.width / bounds.width(), size.height / bounds.height())
    val matrix = Matrix().apply {
        postScale(factor, factor)
        postTranslate(
            size.width / 2f - bounds.centerX() * factor,
            size.height / 2f - bounds.centerY() * factor,
        )
    }
    path.transform(matrix)
    return path.asComposePath()
}
