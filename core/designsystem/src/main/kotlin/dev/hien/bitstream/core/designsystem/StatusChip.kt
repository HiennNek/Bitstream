package dev.hien.bitstream.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.VolumeState

/**
 * Pure label mapping for the digital audio path fact in the Now Playing status
 * bar. Hardware volume at any step preserves bit-perfection; software volume
 * demotes it.
 */
fun pathLabel(
    path: BitPerfectPath,
    volume: VolumeState? = VolumeState.Fixed,
): String = when {
    volume is VolumeState.Software || path == BitPerfectPath.SOFTWARE_VOLUME ->
        "Software volume"
    path == BitPerfectPath.BIT_PERFECT -> "Bit-perfect"
    path == BitPerfectPath.UNSUPPORTED_FORMAT -> "Format mismatch"
    path == BitPerfectPath.NO_DAC -> "No DAC"
    else -> "No DAC"
}

/**
 * Pure label mapping for the FLAC STREAMINFO MD5 integrity check. When software
 * volume is active, a verified decode is labelled `Decode verified` so it never
 * blurs with the path claim.
 */
fun integrityLabel(
    integrity: Integrity,
    volume: VolumeState? = null,
): String = when (integrity) {
    Integrity.VERIFIED ->
        if (volume is VolumeState.Software) "Decode verified" else "MD5 verified"
    Integrity.PARTIAL -> "Partial"
    Integrity.FAILED -> "Failed"
    Integrity.UNAVAILABLE -> "Unavailable"
}

/** Pure label mapping for the USB isochronous underrun counter. */
fun underrunLabel(underruns: Long): String =
    if (underruns == 1L) "1 underrun" else "$underruns underruns"

private enum class FactTone {
    POSITIVE,
    NEUTRAL,
    WARNING,
    ERROR,
}

private enum class FactGlyph {
    CHECK,
    WARNING,
    ERROR,
    INFO,
}

/**
 * Three-fact status bar for Now Playing (Spec §13): Path, Integrity, and
 * Transport underruns. Every fact pairs a distinct vector glyph with text so
 * state is never communicated by color alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusChip(
    path: BitPerfectPath,
    integrity: Integrity,
    underruns: Long,
    volume: VolumeState?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val pathText = pathLabel(path, volume)
    val integrityText = integrityLabel(integrity, volume)
    val underrunText = underrunLabel(underruns)

    val pathTone = when {
        volume is VolumeState.Software || path == BitPerfectPath.SOFTWARE_VOLUME ->
            FactTone.WARNING
        path == BitPerfectPath.BIT_PERFECT -> FactTone.POSITIVE
        path == BitPerfectPath.UNSUPPORTED_FORMAT -> FactTone.ERROR
        else -> FactTone.NEUTRAL
    }
    val pathGlyph = when (pathTone) {
        FactTone.POSITIVE -> FactGlyph.CHECK
        FactTone.WARNING -> FactGlyph.WARNING
        FactTone.ERROR -> FactGlyph.ERROR
        FactTone.NEUTRAL -> FactGlyph.INFO
    }

    val integrityTone = when (integrity) {
        Integrity.VERIFIED -> FactTone.POSITIVE
        Integrity.FAILED -> FactTone.ERROR
        Integrity.PARTIAL, Integrity.UNAVAILABLE -> FactTone.NEUTRAL
    }
    val integrityGlyph = when (integrity) {
        Integrity.VERIFIED -> FactGlyph.CHECK
        Integrity.FAILED -> FactGlyph.ERROR
        Integrity.PARTIAL, Integrity.UNAVAILABLE -> FactGlyph.INFO
    }

    val underrunTone = if (underruns == 0L) FactTone.POSITIVE else FactTone.WARNING
    val underrunGlyph = if (underruns == 0L) FactGlyph.CHECK else FactGlyph.WARNING

    FlowRow(
        modifier = modifier.testTag("status_chip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusFactPill(
            text = pathText,
            tone = pathTone,
            glyph = pathGlyph,
            tag = "status_path_chip",
            onClick = onClick,
        )
        StatusFactPill(
            text = integrityText,
            tone = integrityTone,
            glyph = integrityGlyph,
            tag = "status_integrity_chip",
            onClick = onClick,
        )
        StatusFactPill(
            text = underrunText,
            tone = underrunTone,
            glyph = underrunGlyph,
            tag = "status_underrun_chip",
            onClick = onClick,
        )
    }
}

@Composable
private fun StatusFactPill(
    text: String,
    tone: FactTone,
    glyph: FactGlyph,
    tag: String,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = when (tone) {
        FactTone.POSITIVE -> scheme.primaryContainer
        FactTone.WARNING -> scheme.tertiaryContainer
        FactTone.ERROR -> scheme.errorContainer
        FactTone.NEUTRAL -> scheme.surfaceVariant
    }
    val contentColor = when (tone) {
        FactTone.POSITIVE -> scheme.onPrimaryContainer
        FactTone.WARNING -> scheme.onTertiaryContainer
        FactTone.ERROR -> scheme.onErrorContainer
        FactTone.NEUTRAL -> scheme.onSurfaceVariant
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .testTag(tag)
            .then(clickModifier)
            .semantics { contentDescription = text },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FactIcon(glyph = glyph, tint = contentColor)
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun FactIcon(
    glyph: FactGlyph,
    tint: Color,
) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val w = size.width
        val h = size.height
        when (glyph) {
            FactGlyph.CHECK -> {
                drawCircle(color = tint, radius = w * 0.44f, style = stroke)
                val check = Path().apply {
                    moveTo(w * 0.28f, h * 0.52f)
                    lineTo(w * 0.44f, h * 0.68f)
                    lineTo(w * 0.72f, h * 0.34f)
                }
                drawPath(path = check, color = tint, style = stroke)
            }

            FactGlyph.WARNING -> {
                val triangle = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.9f, h * 0.86f)
                    lineTo(w * 0.1f, h * 0.86f)
                    close()
                }
                drawPath(path = triangle, color = tint, style = stroke)
                val bang = Path().apply {
                    moveTo(w * 0.5f, h * 0.38f)
                    lineTo(w * 0.5f, h * 0.62f)
                }
                drawPath(path = bang, color = tint, style = stroke)
                drawCircle(
                    color = tint,
                    radius = 1.3.dp.toPx(),
                    center = center.copy(y = h * 0.75f),
                )
            }

            FactGlyph.ERROR -> {
                drawCircle(color = tint, radius = w * 0.44f, style = stroke)
                val cross = Path().apply {
                    moveTo(w * 0.34f, h * 0.34f)
                    lineTo(w * 0.66f, h * 0.66f)
                    moveTo(w * 0.66f, h * 0.34f)
                    lineTo(w * 0.34f, h * 0.66f)
                }
                drawPath(path = cross, color = tint, style = stroke)
            }

            FactGlyph.INFO -> {
                drawCircle(color = tint, radius = w * 0.44f, style = stroke)
                val stem = Path().apply {
                    moveTo(w * 0.5f, h * 0.46f)
                    lineTo(w * 0.5f, h * 0.72f)
                }
                drawPath(path = stem, color = tint, style = stroke)
                drawCircle(
                    color = tint,
                    radius = 1.3.dp.toPx(),
                    center = center.copy(y = h * 0.31f),
                )
            }
        }
    }
}
