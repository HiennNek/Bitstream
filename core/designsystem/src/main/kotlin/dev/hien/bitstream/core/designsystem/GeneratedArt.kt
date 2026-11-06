package dev.hien.bitstream.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The mockup rotates the lobe count per track: 6..14 sides. */
fun artSides(seed: Long): Int = 6 + Math.floorMod(seed, 9L).toInt()

/**
 * The mockup's generated cover: a smooth lobed blob in the album's primary with
 * a smaller tertiary-container blob and a primary dot in the middle.
 */
@Composable
fun GeneratedCoverArt(hue: Int, seed: Long, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = remember(hue, dark) { trackPalette(hue, dark) }
    val sides = remember(seed) { artSides(seed) }
    Canvas(modifier) {
        val unit = size.minDimension / 100f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawPath(
            path = blobPath(sides, 46.5f, 0.07f, 0f, unit, center),
            color = Color(palette.primary),
        )
        drawPath(
            path = blobPath(sides, 29f, 0.09f, PI.toFloat() / sides, unit, center),
            color = Color(palette.tertiaryContainer),
        )
        drawCircle(Color(palette.primary), radius = 9f * unit, center = center)
    }
}

/** A single lobed blob used behind tile and chip glyphs. */
@Composable
fun BlobBackground(
    sides: Int,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val unit = size.minDimension / 100f
            drawPath(
                path = blobPath(
                    sides = sides,
                    radius = 46.5f,
                    amplitude = 0.07f,
                    rotation = 0f,
                    unit = unit,
                    center = Offset(size.width / 2f, size.height / 2f),
                ),
                color = color,
            )
        }
        content()
    }
}

private fun DrawScope.blobPath(
    sides: Int,
    radius: Float,
    amplitude: Float,
    rotation: Float,
    unit: Float,
    center: Offset,
): Path {
    val path = Path()
    for (i in 0..160) {
        val angle = i / 160f * (2f * PI.toFloat())
        val r = radius * (1f + amplitude * cos(sides * angle + rotation))
        val x = center.x + unit * r * cos(angle)
        val y = center.y + unit * r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
