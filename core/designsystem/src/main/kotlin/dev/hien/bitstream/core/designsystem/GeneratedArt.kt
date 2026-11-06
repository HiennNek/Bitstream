package dev.hien.bitstream.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.graphics.shapes.RoundedPolygon

/** Inner flower and the half lobe step that keeps its petals off-axis. */
private class InnerShape(val polygon: RoundedPolygon, val halfStepDegrees: Float)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val innerShapes = listOf(
    InnerShape(MaterialShapes.Flower, 22.5f),
    InnerShape(MaterialShapes.Cookie6Sided, 30f),
    InnerShape(MaterialShapes.Clover8Leaf, 22.5f),
)

/**
 * The mockup's generated cover: the outer cookie is the clip on the caller's
 * layer, this fills it with the album's primary, a smaller tertiary-container
 * flower rotated half a lobe step, and a primary dot in the middle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GeneratedCoverArt(hue: Int, seed: Long, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = remember(hue, dark) { trackPalette(hue, dark) }
    val inner = remember(seed) {
        innerShapes[Math.floorMod(seed, innerShapes.size.toLong()).toInt()]
    }
    val innerShape = inner.polygon.toShape()

    Box(
        modifier = modifier.background(Color(palette.primary)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.6f)
                .graphicsLayer {
                    rotationZ = inner.halfStepDegrees
                    shape = innerShape
                    clip = true
                }
                .background(Color(palette.tertiaryContainer)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.18f)
                .background(Color(palette.primary), CircleShape),
        )
    }
}
