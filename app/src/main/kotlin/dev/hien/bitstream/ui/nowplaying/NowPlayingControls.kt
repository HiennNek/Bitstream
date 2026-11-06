package dev.hien.bitstream.ui.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.scaleOnPress
import dev.hien.bitstream.core.model.RepeatMode

/** Per-layout transport dimensions copied from the mockup artboards. */
data class TransportMetrics(
    val inlineModes: Boolean,
    val modeSize: Dp,
    val modeIcon: Dp,
    val sideWidth: Dp,
    val height: Dp,
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
    sideWidth = 92.dp,
    height = 80.dp,
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
    sideWidth = 76.dp,
    height = 72.dp,
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
    sideWidth = 112.dp,
    height = 88.dp,
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
    sideWidth = 92.dp,
    height = 80.dp,
    playWidth = 132.dp,
    playRadiusPlaying = 28.dp,
    playRadiusPaused = 40.dp,
    sideIcon = 36.dp,
    playIcon = 42.dp,
    gap = 20.dp,
)

/** Transport and mode toggles, using connected Material 3 shapes. */
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
    val shuffleToggle: @Composable (Modifier) -> Unit = { toggleModifier ->
        ModeToggle(
            checked = shuffle,
            onClick = onToggleShuffle,
            painter = R.drawable.ic_shuffle,
            description = if (shuffle) "Shuffle on" else "Shuffle off",
            iconSize = metrics.modeIcon,
            modifier = toggleModifier.testTag("now_playing_shuffle"),
        )
    }
    val repeatToggle: @Composable (Modifier) -> Unit = { toggleModifier ->
        ModeToggle(
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
            iconSize = metrics.modeIcon,
            modifier = toggleModifier.testTag("now_playing_repeat"),
        )
    }

    if (metrics.inlineModes) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shuffleToggle(Modifier.size(metrics.modeSize))
            TransportGroup(
                metrics = metrics,
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
            )
            repeatToggle(Modifier.size(metrics.modeSize))
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TransportGroup(
                    metrics = metrics,
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shuffleToggle(Modifier.weight(1f).height(metrics.modeSize))
                repeatToggle(Modifier.weight(1f).height(metrics.modeSize))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportGroup(
    metrics: TransportMetrics,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val previousSource = remember { MutableInteractionSource() }
    val playSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    val leadingShape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape
    val trailingShape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape

    ButtonGroup(
        overflowIndicator = {},
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        customItem(
            buttonGroupContent = {
                SideButton(
                    onClick = onPrevious,
                    previous = true,
                    metrics = metrics,
                    shape = leadingShape,
                    interactionSource = previousSource,
                    modifier = Modifier.animateWidth(previousSource),
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                PlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPause,
                    metrics = metrics,
                    interactionSource = playSource,
                    modifier = Modifier.animateWidth(playSource),
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                SideButton(
                    onClick = onNext,
                    previous = false,
                    metrics = metrics,
                    shape = trailingShape,
                    interactionSource = nextSource,
                    modifier = Modifier.animateWidth(nextSource),
                )
            },
            menuContent = {},
        )
    }
}

@Composable
private fun SideButton(
    onClick: () -> Unit,
    previous: Boolean,
    metrics: TransportMetrics,
    shape: androidx.compose.ui.graphics.Shape,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        interactionSource = interactionSource,
        modifier = modifier
            .scaleOnPress(interactionSource)
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
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
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
        interactionSource = interactionSource,
        modifier = modifier
            .scaleOnPress(interactionSource)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeToggle(
    checked: Boolean,
    onClick: () -> Unit,
    painter: Int,
    description: String,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        contentPadding = PaddingValues(0.dp),
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(painter),
            contentDescription = description,
            modifier = Modifier.size(iconSize),
        )
    }
}
