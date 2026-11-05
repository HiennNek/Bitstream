package dev.hien.bitstream.ui.nowplaying

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.designsystem.StatusChip
import dev.hien.bitstream.core.designsystem.WaveformSeek
import dev.hien.bitstream.core.designsystem.formatPlaybackTime
import dev.hien.bitstream.core.designsystem.pathLabel
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.playback.usb.DacState
import dev.hien.bitstream.ui.components.CoverArt
import dev.hien.bitstream.ui.library.FavoritesViewModel
import dev.hien.bitstream.ui.queue.QueuePane
import dev.hien.bitstream.ui.queue.QueueSheet
import dev.hien.bitstream.ui.queue.QueueViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingRoute(
    onBack: (() -> Unit)? = null,
    onQueue: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val container = LocalAppContainer.current
    val viewModel: NowPlayingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NowPlayingViewModel(container.playbackEngine) }
        },
    )
    val favoritesViewModel: FavoritesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FavoritesViewModel(container.musicRepository) }
        },
    )
    val queueViewModel: QueueViewModel = viewModel(
        factory = viewModelFactory {
            initializer { QueueViewModel(container.playbackEngine) }
        },
    )
    val state by viewModel.state.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val favoritesState by favoritesViewModel.state.collectAsState()
    val queueState by queueViewModel.state.collectAsState()
    val dacState by container.usbDeviceManager.state.collectAsState()
    var showOutputSheet by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }

    val effectiveDacName = state.dacName ?: (dacState as? DacState.Connected)?.handle?.name
    val effectiveState = state.copy(dacName = effectiveDacName)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 840.dp
        NowPlayingContent(
            state = effectiveState,
            positionMs = positionMs,
            durationMs = durationMs,
            favoriteIds = favoritesState.favoriteIds,
            onBack = onBack,
            onPlayPause = viewModel::togglePlayPause,
            onSeek = viewModel::seekTo,
            onNext = viewModel::next,
            onPrevious = viewModel::previous,
            onToggleShuffle = viewModel::toggleShuffle,
            onCycleRepeat = viewModel::cycleRepeat,
            onToggleFavorite = favoritesViewModel::toggle,
            onHardwareVolume = viewModel::setHardwareVolume,
            onSoftwareVolume = viewModel::setSoftwareVolume,
            onSoftwareVolumeLevel = viewModel::setSoftwareVolumeLevel,
            onQueue = {
                showQueue = !showQueue
                onQueue()
            },
            onOutput = { showOutputSheet = true },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            supportingQueuePane = if (isExpandedWidth && showQueue) {
                {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(Radii.xl),
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight(),
                    ) {
                        QueuePane(
                            state = queueState,
                            onMove = queueViewModel::move,
                            onRemove = queueViewModel::remove,
                            onPlay = queueViewModel::play,
                            onToggleShuffle = queueViewModel::toggleShuffle,
                            onCycleRepeat = queueViewModel::cycleRepeat,
                            onClose = { showQueue = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                null
            },
        )

        if (!isExpandedWidth && showQueue) {
            QueueSheet(onDismiss = { showQueue = false })
        }
    }

    if (showOutputSheet) {
        OutputSheet(
            state = effectiveState,
            dacState = dacState,
            onSoftwareVolume = viewModel::setSoftwareVolume,
            onDismiss = { showOutputSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingContent(
    state: NowPlayingUiState,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    favoriteIds: Set<Long> = emptySet(),
    onBack: (() -> Unit)?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Long) -> Unit = {},
    onHardwareVolume: (Int) -> Unit = {},
    onSoftwareVolume: (Boolean) -> Unit = {},
    onSoftwareVolumeLevel: (Int) -> Unit = {},
    onQueue: () -> Unit,
    onOutput: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    supportingQueuePane: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.testTag("now_playing_screen")) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NowPlayingHeader(
                folder = state.track?.folder.orEmpty(),
                onBack = onBack,
                onOutput = onOutput,
            )
            StatusChip(
                path = state.path,
                integrity = state.integrity,
                underruns = state.underruns,
                volume = state.volume,
                onClick = onOutput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                val sideBySide = maxWidth >= 600.dp
                if (sideBySide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.xl)
                            .testTag("now_playing_side_by_side"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxl),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NowPlayingArtwork(
                                track = state.track,
                                spinning = state.isPlaying,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                modifier = Modifier
                                    .widthIn(max = 310.dp)
                                    .aspectRatio(1f),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.55f)
                                .widthIn(max = 560.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            NowPlayingControlsPane(
                                state = state,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                favoriteIds = favoriteIds,
                                onPlayPause = onPlayPause,
                                onSeek = onSeek,
                                onNext = onNext,
                                onPrevious = onPrevious,
                                onToggleShuffle = onToggleShuffle,
                                onCycleRepeat = onCycleRepeat,
                                onToggleFavorite = onToggleFavorite,
                                onHardwareVolume = onHardwareVolume,
                                onSoftwareVolume = onSoftwareVolume,
                                onSoftwareVolumeLevel = onSoftwareVolumeLevel,
                                onQueue = onQueue,
                            )
                        }
                        if (supportingQueuePane != null) {
                            supportingQueuePane()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.xl)
                            .testTag("now_playing_stacked"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        NowPlayingArtwork(
                            track = state.track,
                            spinning = state.isPlaying,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .widthIn(max = 290.dp)
                                .aspectRatio(1f),
                        )
                        NowPlayingControlsPane(
                            state = state,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            favoriteIds = favoriteIds,
                            onPlayPause = onPlayPause,
                            onSeek = onSeek,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                            onToggleFavorite = onToggleFavorite,
                            onHardwareVolume = onHardwareVolume,
                            onSoftwareVolume = onSoftwareVolume,
                            onSoftwareVolumeLevel = onSoftwareVolumeLevel,
                            onQueue = onQueue,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(
    folder: String,
    onBack: (() -> Unit)?,
    onOutput: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Close now playing",
                )
            }
        } else {
            Box(modifier = Modifier.size(48.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Playing from",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = folder.ifBlank { "Library" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOutput) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "Audio output",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingControlsPane(
    state: NowPlayingUiState,
    positionMs: Long,
    durationMs: Long,
    favoriteIds: Set<Long>,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onHardwareVolume: (Int) -> Unit,
    onSoftwareVolume: (Boolean) -> Unit,
    onSoftwareVolumeLevel: (Int) -> Unit,
    onQueue: () -> Unit,
) {
    val track = state.track
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
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
            if (track != null) {
                val favorite = track.id in favoriteIds
                IconToggleButton(
                    checked = favorite,
                    onCheckedChange = { onToggleFavorite(track.id) },
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("now_playing_favorite"),
                ) {
                    Icon(
                        painter = painterResource(
                            if (favorite) {
                                R.drawable.ic_favorite
                            } else {
                                R.drawable.ic_favorite_border
                            },
                        ),
                        contentDescription = if (favorite) {
                            "Remove from favorites"
                        } else {
                            "Add to favorites"
                        },
                        tint = if (favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        WaveformSeek(
            positionMs = positionMs,
            durationMs = durationMs,
            playing = state.isPlaying,
            onSeek = onSeek,
            modifier = Modifier.testTag("now_playing_seek_slider"),
        )

        NowPlayingControls(
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
            onSoftwareVolumeLevel = onSoftwareVolumeLevel,
        )

        if (state.notices.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(Radii.sm),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.notices.last(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }

        UpNextPeek(queueSize = state.queueSize, onQueue = onQueue)
    }
}

@Composable
private fun UpNextPeek(queueSize: Int, onQueue: () -> Unit) {
    Surface(
        onClick = onQueue,
        shape = RoundedCornerShape(Radii.tile),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("now_playing_up_next"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (queueSize > 0) {
                        if (queueSize == 1) "1 track in queue" else "$queueSize tracks in queue"
                    } else {
                        "Queue is empty"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_playlist_play),
                contentDescription = "Open queue",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingArtwork(
    track: Track?,
    spinning: Boolean = false,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "now_playing_artwork"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(sharedModifier)
            .semantics { contentDescription = "Artwork for ${track?.title ?: "no track"}" },
        contentAlignment = Alignment.Center,
    ) {
        if (track != null) {
            CoverArt(
                track = track,
                spinning = spinning,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Radii.xxl))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "FLAC",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Persistent mini player bar anchored above the bottom navigation bar or inside
 * the main content scaffold.
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
        shape = RoundedCornerShape(Radii.lg),
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
            CoverArt(
                track = track,
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
                Icon(
                    painter = painterResource(
                        if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.testTag("mini_player_next"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = "Next track",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
