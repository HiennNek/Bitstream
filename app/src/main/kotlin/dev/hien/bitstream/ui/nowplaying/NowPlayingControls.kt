package dev.hien.bitstream.ui.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.VolumeState

/**
 * Transport and mode toggles. Shapes and timing come from the Material motion
 * scheme; only the asymmetric pill radii are ours.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingControls(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeat: RepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>()
    val playCorner by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 40.dp,
        animationSpec = spatial,
        label = "playCorner",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onPrevious,
                shape = RoundedCornerShape(
                    topStart = 40.dp,
                    topEnd = 14.dp,
                    bottomEnd = 14.dp,
                    bottomStart = 40.dp,
                ),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(0.9f)
                    .height(80.dp)
                    .testTag("now_playing_previous"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "Previous track",
                    )
                }
            }

            Surface(
                onClick = onPlayPause,
                shape = RoundedCornerShape(playCorner),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .weight(1.2f)
                    .height(80.dp)
                    .testTag("now_playing_play_pause")
                    .semantics {
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.7f))
                                .togetherWith(fadeOut())
                        },
                        label = "playPauseIcon",
                    ) { playing ->
                        Icon(
                            painter = painterResource(
                                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Surface(
                onClick = onNext,
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 40.dp,
                    bottomEnd = 40.dp,
                    bottomStart = 14.dp,
                ),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(0.9f)
                    .height(80.dp)
                    .testTag("now_playing_next"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next track",
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconToggleButton(
                checked = shuffle,
                onCheckedChange = { onToggleShuffle() },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("now_playing_shuffle"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = if (shuffle) "Shuffle on" else "Shuffle off",
                    tint = if (shuffle) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconToggleButton(
                checked = repeat != RepeatMode.OFF,
                onCheckedChange = { onCycleRepeat() },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("now_playing_repeat"),
            ) {
                Icon(
                    painter = painterResource(
                        if (repeat == RepeatMode.ONE) {
                            R.drawable.ic_repeat_one
                        } else {
                            R.drawable.ic_repeat
                        },
                    ),
                    contentDescription = when (repeat) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                    tint = if (repeat != RepeatMode.OFF) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Hardware or software volume, mirroring the engine's current mode. */
@Composable
fun VolumeSection(
    volume: VolumeState?,
    onHardwareVolume: (Int) -> Unit,
    onSoftwareVolume: (Boolean) -> Unit,
    onSoftwareVolumeLevel: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(Radii.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (volume) {
                is VolumeState.Hardware -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Hardware volume (UAC)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "${volume.level}%",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag("hardware_volume_value"),
                        )
                    }
                    Slider(
                        value = volume.level / 100f,
                        onValueChange = { fraction ->
                            onHardwareVolume((fraction * 100f).toInt().coerceIn(0, 100))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hardware_volume_slider"),
                    )
                }

                is VolumeState.Software -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Software volume (${volume.level}%) — not bit-perfect",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "Q1.30 digital attenuation + TPDF dither active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { onSoftwareVolume(false) },
                            modifier = Modifier.testTag("restore_bit_perfect_button"),
                        ) {
                            Text("Restore")
                        }
                    }
                    Slider(
                        value = volume.level / 100f,
                        onValueChange = { fraction ->
                            onSoftwareVolumeLevel((fraction * 100f).toInt().coerceIn(0, 100))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("software_volume_slider"),
                    )
                }

                VolumeState.Fixed, null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Fixed output",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag("fixed_output_label"),
                        )
                        Text(
                            text = "Unity / DAC hardware",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = 1f,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
