package dev.hien.bitstream.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Playback fraction, clamped to 0..1. Zero-length tracks never divide by zero. */
fun seekProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
}

/**
 * A stock [Slider] with a waveform track. Drag, keyboard, and TalkBack semantics
 * all come from Material; only the track and thumb pixels are ours.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WaveformSeek(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Playback position",
) {
    val range = durationMs.coerceAtLeast(1L).toFloat()
    // Re-key the slider on duration: rememberSliderState captures its track range
    // on first composition, so a track change would otherwise keep the old length.
    val sliderState = key(durationMs) {
        rememberSliderState(
            value = positionMs.toFloat().coerceIn(0f, range),
            trackRange = 0f..range,
        )
    }
    LaunchedEffect(positionMs, durationMs, sliderState.isDragging) {
        if (!sliderState.isDragging) {
            sliderState.value = positionMs.toFloat().coerceIn(0f, range)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.secondaryContainer
    val motionEnabled = rememberMotionEnabled()
    val waveMorph = animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = if (motionEnabled) tween(320) else snap(),
        label = "waveMorph",
    ).value
    // Freeze the phase on pause so flattening to a line does not jump sideways.
    val drift = remember { Animatable(0f) }
    LaunchedEffect(playing, motionEnabled) {
        if (!playing || !motionEnabled) return@LaunchedEffect
        while (true) {
            drift.animateTo(
                targetValue = drift.value + (2 * PI).toFloat(),
                animationSpec = tween(1_400, easing = LinearEasing),
            )
        }
    }

    Column(modifier = modifier) {
        Slider(
            state = sliderState,
            onValueChange = { sliderState.value = it },
            onValueChangeFinished = { onSeek(sliderState.value.toLong()) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = contentDescription },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(primary),
                )
            },
            track = { state ->
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                ) {
                    val centerY = size.height / 2f
                    val lineWidth = 5.dp.toPx()
                    val fraction = seekProgress(state.value.toLong(), durationMs)
                    val activeWidth = size.width * fraction
                    val gap = 10.dp.toPx()

                    val remainderStart = (activeWidth + gap).coerceAtMost(size.width)
                    if (remainderStart < size.width) {
                        drawLine(
                            color = inactive,
                            start = Offset(remainderStart, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = lineWidth,
                            cap = StrokeCap.Round,
                        )
                    }

                    if (activeWidth > 0f) {
                        val amplitude = 4.5.dp.toPx() * waveMorph
                        val period = 30.dp.toPx()
                        val path = Path()
                        var x = 0f
                        while (x <= activeWidth) {
                            // Phase is measured from the thumb end, so the wave
                            // stays attached to the bar while it is dragged.
                            val phase = ((x - activeWidth) / period) * 2f * PI.toFloat() + drift.value
                            val y = centerY + sin(phase) * amplitude
                            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                            x += 1f
                        }
                        drawPath(
                            path = path,
                            color = primary,
                            style = Stroke(width = lineWidth, cap = StrokeCap.Round),
                        )
                    }
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPlaybackTime(
                    if (sliderState.isDragging) sliderState.value.toLong() else positionMs,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatPlaybackTime(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
