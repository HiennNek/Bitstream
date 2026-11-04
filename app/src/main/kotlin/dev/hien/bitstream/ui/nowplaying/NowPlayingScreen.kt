package dev.hien.bitstream.ui.nowplaying

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.StatusChip
import dev.hien.bitstream.core.designsystem.expressiveSpatialSpec
import dev.hien.bitstream.core.designsystem.integrityLabel
import dev.hien.bitstream.core.designsystem.pathLabel
import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.VolumeState
import dev.hien.bitstream.core.playback.usb.DacState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingRoute(
    onBack: (() -> Unit)? = null,
    onQueue: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val container = LocalAppContainer.current
    val viewModel = remember(container.playbackEngine) {
        NowPlayingViewModel(container.playbackEngine)
    }
    val state by viewModel.state.collectAsState()
    val dacState by container.usbDeviceManager.state.collectAsState()
    var showOutputSheet by rememberSaveable { mutableStateOf(false) }

    val effectiveDacName = state.dacName ?: (dacState as? DacState.Connected)?.handle?.name
    val effectiveState = state.copy(dacName = effectiveDacName)

    NowPlayingContent(
        state = effectiveState,
        onBack = onBack,
        onPlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeat,
        onHardwareVolume = viewModel::setHardwareVolume,
        onSoftwareVolume = viewModel::setSoftwareVolume,
        onQueue = onQueue,
        onOutput = { showOutputSheet = true },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )

    if (showOutputSheet) {
        OutputSheet(
            state = effectiveState,
            dacState = dacState,
            onSoftwareVolume = viewModel::setSoftwareVolume,
            onDismiss = { showOutputSheet = false },
        )
    }
}

/**
 * Primary Now Playing screen entry point specified by the implementation plan.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    onQueue: () -> Unit,
    onOutput: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = remember(container.playbackEngine) {
        NowPlayingViewModel(container.playbackEngine)
    }
    val state by viewModel.state.collectAsState()
    val dacState by container.usbDeviceManager.state.collectAsState()
    var showInternalOutputSheet by rememberSaveable { mutableStateOf(false) }

    val effectiveDacName = state.dacName ?: (dacState as? DacState.Connected)?.handle?.name
    val effectiveState = state.copy(dacName = effectiveDacName)

    NowPlayingContent(
        state = effectiveState,
        onBack = null,
        onPlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeat,
        onHardwareVolume = viewModel::setHardwareVolume,
        onSoftwareVolume = viewModel::setSoftwareVolume,
        onQueue = onQueue,
        onOutput = {
            showInternalOutputSheet = true
            onOutput()
        },
        sharedTransitionScope = null,
        animatedVisibilityScope = null,
    )

    if (showInternalOutputSheet) {
        OutputSheet(
            state = effectiveState,
            dacState = dacState,
            onSoftwareVolume = viewModel::setSoftwareVolume,
            onDismiss = { showInternalOutputSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingContent(
    state: NowPlayingUiState,
    onBack: (() -> Unit)?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onHardwareVolume: (Int) -> Unit,
    onSoftwareVolume: (Boolean) -> Unit,
    onQueue: () -> Unit,
    onOutput: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("now_playing_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val subtitle = state.dacName ?: "No USB DAC"
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = onOutput,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("now_playing_output_button"),
                    ) {
                        Text(text = state.dacName ?: "Output")
                    }
                    FilledTonalButton(
                        onClick = onQueue,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("now_playing_queue_button"),
                    ) {
                        Text(
                            text = if (state.queueSize > 0) {
                                "Queue (${state.queueSize})"
                            } else {
                                "Queue"
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            val sideBySide = maxWidth >= 600.dp || maxWidth > maxHeight
            if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("now_playing_side_by_side"),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NowPlayingArtwork(
                            artworkUri = state.track?.artworkUri,
                            title = state.track?.title ?: "No track playing",
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .aspectRatio(1f),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .widthIn(max = 560.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        NowPlayingControlsPane(
                            state = state,
                            onPlayPause = onPlayPause,
                            onSeek = onSeek,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                            onHardwareVolume = onHardwareVolume,
                            onSoftwareVolume = onSoftwareVolume,
                            onOutput = onOutput,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .testTag("now_playing_stacked"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NowPlayingArtwork(
                        artworkUri = state.track?.artworkUri,
                        title = state.track?.title ?: "No track playing",
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .widthIn(max = 340.dp)
                            .aspectRatio(1f),
                    )
                    NowPlayingControlsPane(
                        state = state,
                        onPlayPause = onPlayPause,
                        onSeek = onSeek,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onHardwareVolume = onHardwareVolume,
                        onSoftwareVolume = onSoftwareVolume,
                        onOutput = onOutput,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingControlsPane(
    state: NowPlayingUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onHardwareVolume: (Int) -> Unit,
    onSoftwareVolume: (Boolean) -> Unit,
    onOutput: () -> Unit,
) {
    val track = state.track
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("now_playing_title"),
            )
            val artistAlbum = listOfNotNull(
                track?.artist?.takeIf { it.isNotBlank() },
                track?.album?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifEmpty { "Select a FLAC track from your library" }
            Text(
                text = artistAlbum,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.formatLabel.isNotBlank()) {
                Text(
                    text = "FLAC ${state.formatLabel} kHz",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("now_playing_format"),
                )
            }
        }

        StatusChip(
            path = state.path,
            integrity = state.integrity,
            underruns = state.underruns,
            volume = state.volume,
            onClick = onOutput,
        )

        SeekSection(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            enabled = track != null && state.durationMs > 0L,
            onSeek = onSeek,
        )

        TransportRow(
            isPlaying = state.isPlaying,
            shuffle = state.shuffle,
            repeat = state.repeat,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
        )

        VolumeSection(
            volume = state.volume,
            onHardwareVolume = onHardwareVolume,
            onSoftwareVolume = onSoftwareVolume,
        )

        if (state.notices.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.notices.last(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingArtwork(
    artworkUri: String?,
    title: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val spatialSpec = expressiveSpatialSpec<androidx.compose.ui.geometry.Rect>()
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "now_playing_artwork"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spatialSpec },
            )
        }
    } else {
        Modifier
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .then(sharedModifier)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        scheme.primaryContainer,
                        scheme.tertiaryContainer,
                    ),
                ),
            )
            .semantics { contentDescription = "Artwork for $title" },
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "FLAC",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SeekSection(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val progress = if (dragging) {
        dragFraction
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val displayedPositionMs = if (dragging && durationMs > 0L) {
        (dragFraction * durationMs).toLong()
    } else {
        positionMs.coerceAtLeast(0L)
    }
    val remainingMs = (durationMs - displayedPositionMs).coerceAtLeast(0L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { fraction ->
                dragging = true
                dragFraction = fraction.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                if (durationMs > 0L) {
                    onSeek((dragFraction * durationMs).toLong())
                }
                dragging = false
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("now_playing_seek_slider"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatElapsedMs(displayedPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "-${formatElapsedMs(remainingMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeat: RepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val spatialDpSpec = expressiveSpatialSpec< androidx.compose.ui.unit.Dp>()
    val playCorner by animateDpAsState(
        targetValue = if (isPlaying) 18.dp else 32.dp,
        animationSpec = spatialDpSpec,
        label = "playPauseCorner",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onToggleShuffle,
            modifier = Modifier
                .size(48.dp)
                .testTag("now_playing_shuffle"),
        ) {
            Text(
                text = if (shuffle) "SHF•" else "SHF",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(52.dp)
                .testTag("now_playing_previous"),
        ) {
            TransportGlyph(kind = TransportGlyphKind.PREVIOUS, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        Surface(
            onClick = onPlayPause,
            shape = RoundedCornerShape(playCorner),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(64.dp)
                .testTag("now_playing_play_pause")
                .semantics {
                    contentDescription = if (isPlaying) "Pause" else "Play"
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                TransportGlyph(
                    kind = if (isPlaying) TransportGlyphKind.PAUSE else TransportGlyphKind.PLAY,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier
                .size(52.dp)
                .testTag("now_playing_next"),
        ) {
            TransportGlyph(kind = TransportGlyphKind.NEXT, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        FilledTonalIconButton(
            onClick = onCycleRepeat,
            modifier = Modifier
                .size(48.dp)
                .testTag("now_playing_repeat"),
        ) {
            val label = when (repeat) {
                RepeatMode.OFF -> "RPT"
                RepeatMode.ALL -> "ALL"
                RepeatMode.ONE -> "1×"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private enum class TransportGlyphKind {
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
}

@Composable
private fun TransportGlyph(
    kind: TransportGlyphKind,
    tint: Color,
) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
            TransportGlyphKind.PLAY -> {
                val triangle = Path().apply {
                    moveTo(w * 0.28f, h * 0.18f)
                    lineTo(w * 0.80f, h * 0.50f)
                    lineTo(w * 0.28f, h * 0.82f)
                    close()
                }
                drawPath(path = triangle, color = tint)
            }

            TransportGlyphKind.PAUSE -> {
                val barWidth = w * 0.20f
                val barHeight = h * 0.64f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.24f, h * 0.18f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.56f, h * 0.18f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }

            TransportGlyphKind.PREVIOUS -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.22f),
                    size = Size(w * 0.10f, h * 0.56f),
                    cornerRadius = CornerRadius(2f, 2f),
                )
                val triangle = Path().apply {
                    moveTo(w * 0.78f, h * 0.22f)
                    lineTo(w * 0.34f, h * 0.50f)
                    lineTo(w * 0.78f, h * 0.78f)
                    close()
                }
                drawPath(path = triangle, color = tint)
            }

            TransportGlyphKind.NEXT -> {
                val triangle = Path().apply {
                    moveTo(w * 0.22f, h * 0.22f)
                    lineTo(w * 0.66f, h * 0.50f)
                    lineTo(w * 0.22f, h * 0.78f)
                    close()
                }
                drawPath(path = triangle, color = tint)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.70f, h * 0.22f),
                    size = Size(w * 0.10f, h * 0.56f),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
        }
    }
}

@Composable
private fun VolumeSection(
    volume: VolumeState?,
    onHardwareVolume: (Int) -> Unit,
    onSoftwareVolume: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
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
                                text = "Software volume (not bit-perfect)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "Disable software volume to restore bit-perfect output.",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutputSheet(
    state: NowPlayingUiState,
    dacState: DacState,
    onSoftwareVolume: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("output_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Audio Output & Signal Path",
                style = MaterialTheme.typography.titleLarge,
            )
            val connected = dacState as? DacState.Connected
            Text(
                text = connected?.handle?.name ?: state.dacName ?: "No USB DAC connected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (connected != null) {
                val device = connected.device
                val formatSummary = device.formats.firstOrNull()?.let { fmt ->
                    "${fmt.channels} ch · ${fmt.subslotBytes * 8}-bit subslot (${fmt.bitResolution} valid bits)"
                } ?: "Standard PCM"
                Text(
                    text = "UAC ${device.uacVersion}.0 · ${device.syncType} · Alt ${device.altSetting} · $formatSummary",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.format != null) {
                Text(
                    text = "Negotiated stream: ${state.format.sampleRate} Hz / ${state.format.bitDepth}-bit (${state.formatLabel})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Path: ${pathLabel(state.path, state.volume)} · Integrity: ${integrityLabel(state.integrity, state.volume)} · ${state.underruns} underruns",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Software volume (not bit-perfect)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Applies digital attenuation before USB output and demotes the Bit-perfect badge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.volume is VolumeState.Software ||
                        state.path == BitPerfectPath.SOFTWARE_VOLUME,
                    onCheckedChange = onSoftwareVolume,
                    modifier = Modifier.testTag("software_volume_switch"),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Persistent mini player bar anchored above the bottom navigation bar or inside
 * the main content scaffold. Shares its artwork element with [NowPlayingArtwork].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayerBar(
    state: NowPlayingUiState,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpenNowPlaying)
            .testTag("mini_player_bar"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NowPlayingArtwork(
                artworkUri = track.artworkUri,
                title = track.title,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier.size(48.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    if (track.artist.isNotBlank()) append(track.artist)
                    if (state.formatLabel.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(state.formatLabel)
                    }
                    append(" · ")
                    append(pathLabel(state.path, state.volume))
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.testTag("mini_player_play_pause"),
            ) {
                TransportGlyph(
                    kind = if (state.isPlaying) TransportGlyphKind.PAUSE else TransportGlyphKind.PLAY,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.testTag("mini_player_next"),
            ) {
                TransportGlyph(
                    kind = TransportGlyphKind.NEXT,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun formatElapsedMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
