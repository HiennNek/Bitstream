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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hien.bitstream.R
import dev.hien.bitstream.core.model.RepeatMode

/** Per-layout transport dimensions copied from the mockup artboards. */
data class TransportMetrics(
    val inlineModes: Boolean,
    val modeSize: Dp,
    val modeIcon: Dp,
    val modeOnRadius: Dp,
    val modeOffRadius: Dp,
    val sideWidth: Dp,
    val height: Dp,
    val sideRadius: Dp,
    val sideInnerRadius: Dp,
    val playWidth: Dp,
    val playRadiusPlaying: Dp,
    val playRadiusPaused: Dp,
    val sideIcon: Dp,
    val playIcon: Dp,
    val gap: Dp,
)

val PhoneTransport = TransportMetrics(
    inlineModes = false,
    modeSize = 56.dp,
    modeIcon = 24.dp,
    modeOnRadius = 18.dp,
    modeOffRadius = 28.dp,
    sideWidth = 92.dp,
    height = 80.dp,
    sideRadius = 40.dp,
    sideInnerRadius = 14.dp,
    playWidth = 124.dp,
    playRadiusPlaying = 28.dp,
    playRadiusPaused = 40.dp,
    sideIcon = 36.dp,
    playIcon = 40.dp,
    gap = 6.dp,
)

val PhoneLandscapeTransport = TransportMetrics(
    inlineModes = true,
    modeSize = 56.dp,
    modeIcon = 26.dp,
    modeOnRadius = 18.dp,
    modeOffRadius = 28.dp,
    sideWidth = 76.dp,
    height = 72.dp,
    sideRadius = 36.dp,
    sideInnerRadius = 14.dp,
    playWidth = 112.dp,
    playRadiusPlaying = 26.dp,
    playRadiusPaused = 36.dp,
    sideIcon = 32.dp,
    playIcon = 38.dp,
    gap = 16.dp,
)

val TabletPortraitTransport = TransportMetrics(
    inlineModes = true,
    modeSize = 64.dp,
    modeIcon = 28.dp,
    modeOnRadius = 20.dp,
    modeOffRadius = 32.dp,
    sideWidth = 112.dp,
    height = 88.dp,
    sideRadius = 44.dp,
    sideInnerRadius = 16.dp,
    playWidth = 164.dp,
    playRadiusPlaying = 32.dp,
    playRadiusPaused = 44.dp,
    sideIcon = 40.dp,
    playIcon = 46.dp,
    gap = 24.dp,
)

val TabletLandscapeTransport = TransportMetrics(
    inlineModes = true,
    modeSize = 56.dp,
    modeIcon = 30.dp,
    modeOnRadius = 18.dp,
    modeOffRadius = 28.dp,
    sideWidth = 92.dp,
    height = 80.dp,
    sideRadius = 40.dp,
    sideInnerRadius = 14.dp,
    playWidth = 132.dp,
    playRadiusPlaying = 28.dp,
    playRadiusPaused = 40.dp,
    sideIcon = 36.dp,
    playIcon = 42.dp,
    gap = 20.dp,
)

/** Transport and mode toggles, with the mockup's asymmetric pill radii. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingControls(
    metrics: TransportMetrics,
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
    val shuffleButton: @Composable () -> Unit = {
        ModeButton(
            checked = shuffle,
            onClick = onToggleShuffle,
            painter = R.drawable.ic_shuffle,
            description = if (shuffle) "Shuffle on" else "Shuffle off",
            onRadius = metrics.modeOnRadius,
            offRadius = metrics.modeOffRadius,
            iconSize = metrics.modeIcon,
            modifier = Modifier
                .size(metrics.modeSize)
                .testTag("now_playing_shuffle"),
        )
    }
    val repeatButton: @Composable () -> Unit = {
        ModeButton(
            checked = repeat != RepeatMode.OFF,
            onClick = onCycleRepeat,
            painter = if (repeat == RepeatMode.ONE) {
                R.drawable.ic_repeat_one
            } else {
                R.drawable.ic_repeat
            },
            description = when (repeat) {
                RepeatMode.OFF -> "Repeat off"
                RepeatMode.ALL -> "Repeat all"
                RepeatMode.ONE -> "Repeat one"
            },
            onRadius = metrics.modeOnRadius,
            offRadius = metrics.modeOffRadius,
            iconSize = metrics.modeIcon,
            modifier = Modifier
                .size(metrics.modeSize)
                .testTag("now_playing_repeat"),
        )
    }

    if (metrics.inlineModes) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shuffleButton()
            SideButton(onPrevious, previous = true, metrics = metrics)
            PlayButton(isPlaying, onPlayPause, metrics)
            SideButton(onNext, previous = false, metrics = metrics)
            repeatButton()
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideButton(onPrevious, previous = true, metrics = metrics)
                PlayButton(isPlaying, onPlayPause, metrics)
                SideButton(onNext, previous = false, metrics = metrics)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeButton(
                    checked = shuffle,
                    onClick = onToggleShuffle,
                    painter = R.drawable.ic_shuffle,
                    description = if (shuffle) "Shuffle on" else "Shuffle off",
                    onRadius = 18.dp,
                    offRadius = 28.dp,
                    iconSize = 24.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(metrics.modeSize)
                        .testTag("now_playing_shuffle"),
                )
                ModeButton(
                    checked = repeat != RepeatMode.OFF,
                    onClick = onCycleRepeat,
                    painter = if (repeat == RepeatMode.ONE) {
                        R.drawable.ic_repeat_one
                    } else {
                        R.drawable.ic_repeat
                    },
                    description = when (repeat) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                    onRadius = 18.dp,
                    offRadius = 28.dp,
                    iconSize = 24.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(metrics.modeSize)
                        .testTag("now_playing_repeat"),
                )
            }
        }
    }
}

@Composable
private fun SideButton(
    onClick: () -> Unit,
    previous: Boolean,
    metrics: TransportMetrics,
) {
    val radius = metrics.sideRadius
    val inner = metrics.sideInnerRadius
    val shape = if (previous) {
        RoundedCornerShape(
            topStart = radius,
            topEnd = inner,
            bottomEnd = inner,
            bottomStart = radius,
        )
    } else {
        RoundedCornerShape(
            topStart = inner,
            topEnd = radius,
            bottomEnd = radius,
            bottomStart = inner,
        )
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .size(width = metrics.sideWidth, height = metrics.height)
            .testTag(if (previous) "now_playing_previous" else "now_playing_next"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(
                    if (previous) R.drawable.ic_skip_previous else R.drawable.ic_skip_next,
                ),
                contentDescription = if (previous) "Previous track" else "Next track",
                modifier = Modifier.size(metrics.sideIcon),
            )
        }
    }
}

@Composable
private fun PlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    metrics: TransportMetrics,
) {
    val corner by animateDpAsState(
        targetValue = if (isPlaying) metrics.playRadiusPlaying else metrics.playRadiusPaused,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "playCorner",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .size(width = metrics.playWidth, height = metrics.height)
            .testTag("now_playing_play_pause")
            .semantics {
                contentDescription = if (isPlaying) "Pause" else "Play"
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.7f)).togetherWith(fadeOut())
                },
                label = "playPauseIcon",
            ) { playing ->
                Icon(
                    painter = painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(metrics.playIcon),
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    checked: Boolean,
    onClick: () -> Unit,
    painter: Int,
    description: String,
    onRadius: Dp,
    offRadius: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val radius by animateDpAsState(
        targetValue = if (checked) onRadius else offRadius,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "modeCorner",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(radius),
        color = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (checked) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(painter),
                contentDescription = description,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
