package dev.hien.bitstream.ui.nowplaying

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.WaveformSeek
import dev.hien.bitstream.core.designsystem.formatPlaybackTime
import dev.hien.bitstream.core.designsystem.pathLabel
import dev.hien.bitstream.core.designsystem.rememberMotionEnabled
import dev.hien.bitstream.core.designsystem.scaleOnPress
import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.playback.usb.DacState
import dev.hien.bitstream.ui.components.CoverArt
import dev.hien.bitstream.ui.library.FavoritesViewModel
import dev.hien.bitstream.ui.library.LibraryViewModel
import dev.hien.bitstream.ui.library.TrackRow
import dev.hien.bitstream.ui.queue.QueueSheet
import dev.hien.bitstream.ui.queue.QueueUiState
import dev.hien.bitstream.ui.queue.QueueViewModel

enum class NowPlayingLayout { PhonePortrait, PhoneLandscape, TabletPortrait, TabletLandscape }

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
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LibraryViewModel(container.musicRepository, container.scanning) }
        },
    )
    val state by viewModel.state.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val favoritesState by favoritesViewModel.state.collectAsState()
    val queueState by queueViewModel.state.collectAsState()
    val libraryState by libraryViewModel.state.collectAsState()
    val dacState by container.usbDeviceManager.state.collectAsState()
    var showOutputSheet by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }

    val effectiveDacName = state.dacName ?: (dacState as? DacState.Connected)?.handle?.name
    val effectiveState = state.copy(dacName = effectiveDacName)
    val upNext = remember(queueState) { queueState.upNext() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = when {
            maxWidth >= 840.dp && maxHeight >= 600.dp -> NowPlayingLayout.TabletLandscape
            maxHeight < 600.dp -> NowPlayingLayout.PhoneLandscape
            maxWidth < 600.dp -> NowPlayingLayout.PhonePortrait
            else -> NowPlayingLayout.TabletPortrait
        }

        val player: @Composable (Modifier) -> Unit = { modifier ->
            NowPlayingContent(
                state = effectiveState,
                positionMs = positionMs,
                durationMs = durationMs,
                favoriteIds = favoritesState.favoriteIds,
                upNext = upNext,
                layout = layout,
                onBack = onBack,
                onPlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeat,
                onToggleFavorite = favoritesViewModel::toggle,
                onQueue = {
                    showQueue = true
                    onQueue()
                },
                onOutput = { showOutputSheet = true },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = modifier,
            )
        }

        if (layout == NowPlayingLayout.TabletLandscape) {
            val songsWidth = if (maxWidth >= 1100.dp) 544.dp else 360.dp
            Row(modifier = Modifier.fillMaxSize()) {
                SongsPane(
                    tracks = libraryState.tracks,
                    width = songsWidth,
                    favoriteIds = favoritesState.favoriteIds,
                    playingId = state.track?.id,
                    isPlaying = state.isPlaying,
                    onTrack = container::playTrack,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp)
                        .testTag("now_playing_pane"),
                ) {
                    player(Modifier.fillMaxSize())
                }
            }
        } else {
            player(Modifier.fillMaxSize())
        }
    }

    if (showQueue) {
        QueueSheet(onDismiss = { showQueue = false })
    }

    if (showOutputSheet) {
        OutputSheet(
            state = effectiveState,
            dacState = dacState,
            onSoftwareVolume = viewModel::setSoftwareVolume,
            onHardwareVolume = viewModel::setHardwareVolume,
            onSoftwareVolumeLevel = viewModel::setSoftwareVolumeLevel,
            onDismiss = { showOutputSheet = false },
        )
    }
}

private fun QueueUiState.upNext(): List<Track> =
    items.drop(currentIndex + 1).take(3)

@Composable
fun NowPlayingContent(
    state: NowPlayingUiState,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    favoriteIds: Set<Long> = emptySet(),
    upNext: List<Track> = emptyList(),
    layout: NowPlayingLayout = NowPlayingLayout.PhonePortrait,
    onBack: (() -> Unit)?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Long) -> Unit = {},
    onQueue: () -> Unit,
    onOutput: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val common = PlayerCallbacks(
        onBack = onBack,
        positionMs = positionMs,
        durationMs = durationMs,
        favoriteIds = favoriteIds,
        upNext = upNext,
        onPlayPause = onPlayPause,
        onSeek = onSeek,
        onNext = onNext,
        onPrevious = onPrevious,
        onToggleShuffle = onToggleShuffle,
        onCycleRepeat = onCycleRepeat,
        onToggleFavorite = onToggleFavorite,
        onQueue = onQueue,
        onOutput = onOutput,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
    Box(modifier = modifier.testTag("now_playing_screen")) {
        when (layout) {
            NowPlayingLayout.PhonePortrait -> PhonePortraitPlayer(state, common)
            NowPlayingLayout.PhoneLandscape -> PhoneLandscapePlayer(state, common)
            NowPlayingLayout.TabletPortrait -> TabletPortraitPlayer(state, common)
            NowPlayingLayout.TabletLandscape -> TabletLandscapePlayer(state, common)
        }
    }
}

private class PlayerCallbacks(
    val onBack: (() -> Unit)?,
    val positionMs: Long,
    val durationMs: Long,
    val favoriteIds: Set<Long>,
    val upNext: List<Track>,
    val onPlayPause: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onToggleFavorite: (Long) -> Unit,
    val onQueue: () -> Unit,
    val onOutput: () -> Unit,
    val sharedTransitionScope: SharedTransitionScope?,
    val animatedVisibilityScope: AnimatedVisibilityScope?,
)

private val panelTitle = TextStyle(
    fontSize = 34.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight(700),
    letterSpacing = (-0.3).sp,
)
private val panelArtist = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight(500))
private val bigTitle = TextStyle(
    fontSize = 44.sp,
    lineHeight = 52.sp,
    fontWeight = FontWeight(700),
    letterSpacing = (-0.5).sp,
)
private val bigArtist = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight(500))
private val landscapeTitle = TextStyle(
    fontSize = 32.sp,
    lineHeight = 38.sp,
    fontWeight = FontWeight(700),
    letterSpacing = (-0.3).sp,
)
private val portraitHeaderLabel = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
private val portraitHeaderFolder = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight(600))
private val landscapeHeaderFolder = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(600))

@Composable
private fun PhonePortraitPlayer(state: NowPlayingUiState, c: PlayerCallbacks) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .testTag("now_playing_stacked"),
    ) {
        NowPlayingHeader(
            folder = state.track?.folder.orEmpty(),
            onBack = c.onBack,
            onOutput = c.onOutput,
            closeSize = 48.dp,
            labelStyle = MaterialTheme.typography.labelMedium,
            folderStyle = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                NowPlayingArtwork(
                    track = state.track,
                    spinning = state.isPlaying,
                    sharedTransitionScope = c.sharedTransitionScope,
                    animatedVisibilityScope = c.animatedVisibilityScope,
                    modifier = Modifier.size(290.dp),
                )
            }
            TrackTitleRow(
                track = state.track,
                favorite = state.track?.let { it.id in c.favoriteIds } == true,
                onToggleFavorite = c.onToggleFavorite,
                titleStyle = MaterialTheme.typography.headlineMedium,
                artistStyle = MaterialTheme.typography.bodyLarge,
                favoriteSize = 56.dp,
                favoriteIcon = 26.dp,
                gap = 16.dp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                WaveformSeek(
                    positionMs = c.positionMs,
                    durationMs = c.durationMs,
                    playing = state.isPlaying,
                    onSeek = c.onSeek,
                    modifier = Modifier
                        .widthIn(max = 342.dp)
                        .fillMaxWidth()
                        .testTag("now_playing_seek_slider"),
                )
            }
            NowPlayingControls(
                metrics = PhoneTransport,
                isPlaying = state.isPlaying,
                shuffle = state.shuffle,
                repeat = state.repeat,
                onPlayPause = c.onPlayPause,
                onPrevious = c.onPrevious,
                onNext = c.onNext,
                onToggleShuffle = c.onToggleShuffle,
                onCycleRepeat = c.onCycleRepeat,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
            Notices(state.notices, Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp))
        }
        UpNextPeek(upNext = c.upNext, onQueue = c.onQueue)
    }
}

@Composable
private fun PhoneLandscapePlayer(state: NowPlayingUiState, c: PlayerCallbacks) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("now_playing_side_by_side"),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
            NowPlayingArtwork(
                track = state.track,
                spinning = state.isPlaying,
                sharedTransitionScope = c.sharedTransitionScope,
                animatedVisibilityScope = c.animatedVisibilityScope,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            NowPlayingHeader(
                folder = state.track?.folder.orEmpty(),
                onBack = c.onBack,
                onOutput = c.onOutput,
                closeSize = 48.dp,
                labelStyle = MaterialTheme.typography.labelSmall,
                folderStyle = landscapeHeaderFolder,
            )
            TrackTitleRow(
                track = state.track,
                favorite = state.track?.let { it.id in c.favoriteIds } == true,
                onToggleFavorite = c.onToggleFavorite,
                titleStyle = landscapeTitle,
                artistStyle = MaterialTheme.typography.bodyLarge,
                favoriteSize = 56.dp,
                favoriteIcon = 26.dp,
                gap = 16.dp,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                WaveformSeek(
                    positionMs = c.positionMs,
                    durationMs = c.durationMs,
                    playing = state.isPlaying,
                    onSeek = c.onSeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("now_playing_seek_slider"),
                )
                NowPlayingControls(
                    metrics = PhoneLandscapeTransport,
                    isPlaying = state.isPlaying,
                    shuffle = state.shuffle,
                    repeat = state.repeat,
                    onPlayPause = c.onPlayPause,
                    onPrevious = c.onPrevious,
                    onNext = c.onNext,
                    onToggleShuffle = c.onToggleShuffle,
                    onCycleRepeat = c.onCycleRepeat,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            UpNextPill(upNext = c.upNext, onQueue = c.onQueue)
        }
    }
}

@Composable
private fun TabletPortraitPlayer(state: NowPlayingUiState, c: PlayerCallbacks) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 40.dp)
            .testTag("now_playing_stacked"),
    ) {
        NowPlayingHeader(
            folder = state.track?.folder.orEmpty(),
            onBack = c.onBack,
            onOutput = c.onOutput,
            closeSize = 56.dp,
            labelStyle = portraitHeaderLabel,
            folderStyle = portraitHeaderFolder,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NowPlayingArtwork(
                    track = state.track,
                    spinning = state.isPlaying,
                    sharedTransitionScope = c.sharedTransitionScope,
                    animatedVisibilityScope = c.animatedVisibilityScope,
                    modifier = Modifier.size(470.dp),
                )
            }
            TrackTitleRow(
                track = state.track,
                favorite = state.track?.let { it.id in c.favoriteIds } == true,
                onToggleFavorite = c.onToggleFavorite,
                titleStyle = bigTitle,
                artistStyle = bigArtist,
                favoriteSize = 64.dp,
                favoriteIcon = 30.dp,
                gap = 20.dp,
                modifier = Modifier.padding(top = 28.dp),
            )
            WaveformSeek(
                positionMs = c.positionMs,
                durationMs = c.durationMs,
                playing = state.isPlaying,
                onSeek = c.onSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("now_playing_seek_slider"),
            )
            NowPlayingControls(
                metrics = TabletPortraitTransport,
                isPlaying = state.isPlaying,
                shuffle = state.shuffle,
                repeat = state.repeat,
                onPlayPause = c.onPlayPause,
                onPrevious = c.onPrevious,
                onNext = c.onNext,
                onToggleShuffle = c.onToggleShuffle,
                onCycleRepeat = c.onCycleRepeat,
                modifier = Modifier.padding(top = 20.dp),
            )
            Notices(state.notices, Modifier.padding(top = 12.dp))
        }
        UpNextTablet(upNext = c.upNext, onQueue = c.onQueue)
    }
}

@Composable
private fun TabletLandscapePlayer(state: NowPlayingUiState, c: PlayerCallbacks) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = c.onQueue) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist_play),
                    contentDescription = "Open queue",
                )
            }
            IconButton(onClick = c.onOutput) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "Audio output",
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            NowPlayingArtwork(
                track = state.track,
                spinning = state.isPlaying,
                sharedTransitionScope = c.sharedTransitionScope,
                animatedVisibilityScope = c.animatedVisibilityScope,
                modifier = Modifier.size(330.dp),
            )
        }
        TrackTitleRow(
            track = state.track,
            favorite = state.track?.let { it.id in c.favoriteIds } == true,
            onToggleFavorite = c.onToggleFavorite,
            titleStyle = panelTitle,
            artistStyle = panelArtist,
            favoriteSize = 56.dp,
            favoriteIcon = 28.dp,
            gap = 16.dp,
            modifier = Modifier.padding(top = 20.dp),
        )
        WaveformSeek(
            positionMs = c.positionMs,
            durationMs = c.durationMs,
            playing = state.isPlaying,
            onSeek = c.onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("now_playing_seek_slider"),
        )
        NowPlayingControls(
            metrics = TabletLandscapeTransport,
            isPlaying = state.isPlaying,
            shuffle = state.shuffle,
            repeat = state.repeat,
            onPlayPause = c.onPlayPause,
            onPrevious = c.onPrevious,
            onNext = c.onNext,
            onToggleShuffle = c.onToggleShuffle,
            onCycleRepeat = c.onCycleRepeat,
            modifier = Modifier.padding(top = 18.dp),
        )
        Notices(state.notices, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun NowPlayingHeader(
    folder: String,
    onBack: (() -> Unit)?,
    onOutput: () -> Unit,
    closeSize: Dp,
    labelStyle: TextStyle,
    folderStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(closeSize)) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Close now playing",
                )
            }
        } else {
            Spacer(modifier = Modifier.size(closeSize))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Playing from",
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = folder.ifBlank { "Library" },
                style = folderStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOutput, modifier = Modifier.size(closeSize)) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "Audio output",
            )
        }
    }
}

@Composable
private fun TrackTitleRow(
    track: Track?,
    favorite: Boolean,
    onToggleFavorite: (Long) -> Unit,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    favoriteSize: Dp,
    favoriteIcon: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track?.title ?: "Nothing playing",
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("now_playing_title"),
            )
            val artist = track?.artist?.takeIf { it.isNotBlank() }
                ?: track?.album?.takeIf { it.isNotBlank() }
                ?: if (track == null) "Select a FLAC track from your library" else ""
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    style = artistStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (track != null) {
            FavoritePill(
                favorite = favorite,
                onClick = { onToggleFavorite(track.id) },
                size = favoriteSize,
                iconSize = favoriteIcon,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoritePill(
    favorite: Boolean,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
) {
    IconToggleButton(
        checked = favorite,
        onCheckedChange = { onClick() },
        shapes = IconButtonDefaults.toggleableShapes(),
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier
            .size(size)
            .testTag("now_playing_favorite"),
    ) {
        Icon(
            painter = painterResource(
                if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
            ),
            contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun Notices(notices: List<String>, modifier: Modifier = Modifier) {
    if (notices.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(Radii.sm),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = notices.last(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun UpNextPeek(upNext: List<Track>, onQueue: () -> Unit) {
    val next = upNext.firstOrNull()
    Surface(
        onClick = onQueue,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("now_playing_up_next"),
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 16.dp)) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight(600),
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (next != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CoverArt(track = next, modifier = Modifier.size(48.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = next.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = next.artist.ifBlank { next.album },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun UpNextPill(upNext: List<Track>, onQueue: () -> Unit) {
    val next = upNext.firstOrNull()
    Surface(
        onClick = onQueue,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("now_playing_up_next"),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (next != null) {
                CoverArt(track = next, modifier = Modifier.size(36.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight(600),
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val label = when {
                    next == null -> "Queue is empty"
                    next.artist.isNotBlank() -> "${next.title} · ${next.artist}"
                    else -> next.title
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (next != null) {
                Text(
                    text = formatPlaybackTime(next.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpNextTablet(upNext: List<Track>, onQueue: () -> Unit) {
    Surface(
        onClick = onQueue,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("now_playing_up_next"),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            Text(
                text = "UP NEXT",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight(600),
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
            if (upNext.isEmpty()) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
            } else {
                upNext.take(3).forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CoverArt(track = track, modifier = Modifier.size(52.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight(600)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist.ifBlank { track.album },
                                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = formatPlaybackTime(track.durationMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongsPane(
    tracks: List<Track>,
    width: Dp,
    favoriteIds: Set<Long>,
    playingId: Long?,
    isPlaying: Boolean,
    onTrack: (Long) -> Unit,
) {
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val visible = if (favoritesOnly) tracks.filter { it.id in favoriteIds } else tracks
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(start = 8.dp, end = 16.dp, top = 32.dp, bottom = 8.dp),
    ) {
        Text(
            text = "Songs",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !favoritesOnly,
                onClick = { favoritesOnly = false },
                label = { Text("All") },
            )
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = true },
                label = { Text("Favorites") },
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("now_playing_song_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(visible, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { onTrack(track.id) },
                    height = 72.dp,
                    radius = 24.dp,
                    startPadding = 20.dp,
                    endPadding = 12.dp,
                    gap = 16.dp,
                    titleStyle = MaterialTheme.typography.titleMedium,
                    leading = {
                        CoverArt(track = track, modifier = Modifier.size(52.dp))
                    },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (track.id == playingId) {
                                PlayingBars(active = isPlaying)
                            }
                            Text(
                                text = formatPlaybackTime(track.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayingBars(active: Boolean) {
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = Modifier.size(width = 16.dp, height = 18.dp)) {
        val barWidth = size.width / 5f
        val heights = floatArrayOf(0.55f, 1f, 0.75f)
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * barWidth * 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
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

private data class MiniMetrics(
    val radius: Dp,
    val marginStart: Dp,
    val marginEnd: Dp,
    val marginTop: Dp,
    val marginBottom: Dp,
    val padStart: Dp,
    val padEnd: Dp,
    val padTop: Dp,
    val padBottom: Dp,
    val art: Dp,
    val titleStyle: TextStyle,
    val playWidth: Dp,
    val playHeight: Dp,
    val playOnRadius: Dp,
    val playOffRadius: Dp,
    val sideButton: Dp,
    val showPrevious: Boolean,
    val progressInset: Dp,
    val progressBottom: Dp,
)

private val miniPhone = MiniMetrics(
    radius = 28.dp,
    marginStart = 12.dp,
    marginEnd = 12.dp,
    marginTop = 0.dp,
    marginBottom = 8.dp,
    padStart = 12.dp,
    padEnd = 16.dp,
    padTop = 10.dp,
    padBottom = 10.dp,
    art = 52.dp,
    titleStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight(600)),
    playWidth = 56.dp,
    playHeight = 52.dp,
    playOnRadius = 18.dp,
    playOffRadius = 26.dp,
    sideButton = 48.dp,
    showPrevious = false,
    progressInset = 22.dp,
    progressBottom = 7.dp,
)

private val miniPhoneLandscape = miniPhone.copy(
    marginTop = 10.dp,
    padEnd = 14.dp,
    padTop = 8.dp,
    padBottom = 8.dp,
    art = 48.dp,
    playWidth = 64.dp,
    playHeight = 48.dp,
    playOnRadius = 18.dp,
    playOffRadius = 24.dp,
    showPrevious = true,
    progressBottom = 6.dp,
)

private val miniTabletLandscape = MiniMetrics(
    radius = 36.dp,
    marginStart = 24.dp,
    marginEnd = 24.dp,
    marginTop = 12.dp,
    marginBottom = 0.dp,
    padStart = 12.dp,
    padEnd = 20.dp,
    padTop = 12.dp,
    padBottom = 12.dp,
    art = 56.dp,
    titleStyle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight(600)),
    playWidth = 76.dp,
    playHeight = 56.dp,
    playOnRadius = 20.dp,
    playOffRadius = 28.dp,
    sideButton = 56.dp,
    showPrevious = true,
    progressInset = 28.dp,
    progressBottom = 7.dp,
)

private val miniTabletPortrait = miniTabletLandscape.copy(
    padStart = 14.dp,
    padEnd = 20.dp,
    padTop = 14.dp,
    padBottom = 14.dp,
    art = 60.dp,
    titleStyle = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight(600)),
    playWidth = 80.dp,
    playHeight = 60.dp,
    playOnRadius = 20.dp,
    playOffRadius = 30.dp,
    progressInset = 30.dp,
    progressBottom = 8.dp,
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerBar(
    state: NowPlayingUiState,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val playInteraction = remember { MutableInteractionSource() }
    val configuration = LocalConfiguration.current
    val metrics = when {
        configuration.screenWidthDp >= 840 && configuration.screenHeightDp >= 600 -> miniTabletLandscape
        configuration.screenWidthDp >= 600 && configuration.screenHeightDp >= 600 -> miniTabletPortrait
        configuration.screenHeightDp < 600 -> miniPhoneLandscape
        else -> miniPhone
    }
    val progress = if (state.duration > 0) {
        state.position.toFloat() / state.duration
    } else {
        0f
    }
    val playCorner by animateDpAsState(
        targetValue = if (state.isPlaying) metrics.playOnRadius else metrics.playOffRadius,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "miniPlayCorner",
    )
    Surface(
        shape = RoundedCornerShape(metrics.radius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = metrics.marginStart,
                end = metrics.marginEnd,
                top = metrics.marginTop,
                bottom = metrics.marginBottom,
            )
            .clickable(onClick = onOpenNowPlaying)
            .testTag("mini_player_bar"),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = metrics.padStart,
                        end = metrics.padEnd,
                        top = metrics.padTop,
                        bottom = metrics.padBottom,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverArt(
                    track = track,
                    modifier = Modifier.size(metrics.art),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = track.title,
                        style = metrics.titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist.ifBlank {
                            pathLabel(state.path, state.volume)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (metrics.showPrevious) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(metrics.sideButton)
                            .testTag("mini_player_previous"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_previous),
                            contentDescription = "Previous track",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    onClick = onPlayPause,
                    shape = RoundedCornerShape(playCorner),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    interactionSource = playInteraction,
                    modifier = Modifier
                        .scaleOnPress(playInteraction)
                        .size(width = metrics.playWidth, height = metrics.playHeight)
                        .testTag("mini_player_play_pause"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                            ),
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                        )
                    }
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(metrics.sideButton)
                        .testTag("mini_player_next"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next track",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val progressModifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = metrics.progressInset, vertical = 0.dp)
                .padding(bottom = metrics.progressBottom)
                .height(4.dp)
            if (state.isPlaying && rememberMotionEnabled()) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    gapSize = 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = progressModifier,
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = progressModifier,
                )
            }
        }
    }
}

private val previewNowPlayingTrack = Track(
    id = 1L,
    uri = "content://preview/1",
    title = "Long Way Home",
    artist = "Neon Meridian",
    album = "Long Way Home",
    albumArtist = "Neon Meridian",
    durationMs = 243_000,
    sampleRate = 96_000,
    bitDepth = 24,
)

private val previewUpNext = listOf(
    Track(
        id = 2L,
        uri = "content://preview/2",
        title = "Glass Harbor",
        artist = "Low Tide Choir",
        album = "Glass Harbor",
        durationMs = 187_000,
    ),
    Track(
        id = 3L,
        uri = "content://preview/3",
        title = "Paper Satellites",
        artist = "Aster Field",
        album = "Paper Satellites",
        durationMs = 251_000,
    ),
    Track(
        id = 4L,
        uri = "content://preview/4",
        title = "Night Bus",
        artist = "Slow Cinema",
        album = "Night Bus",
        durationMs = 205_000,
    ),
)

private val previewNowPlayingState = NowPlayingUiState(
    status = PlaybackStatus.READY,
    track = previewNowPlayingTrack,
    position = 69_000,
    duration = 243_000,
    path = BitPerfectPath.BIT_PERFECT,
    integrity = Integrity.VERIFIED,
    dacName = "USB DAC",
    shuffle = true,
    repeat = RepeatMode.ALL,
    queueSize = 4,
)

@Preview(name = "Phone portrait light", widthDp = 393, heightDp = 851, showBackground = true)
@Preview(
    name = "Phone portrait dark",
    widthDp = 393,
    heightDp = 851,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Phone landscape light", widthDp = 851, heightDp = 393, showBackground = true)
@Preview(
    name = "Phone landscape dark",
    widthDp = 851,
    heightDp = 393,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Tablet portrait light", widthDp = 800, heightDp = 1280, showBackground = true)
@Preview(
    name = "Tablet portrait dark",
    widthDp = 800,
    heightDp = 1280,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Tablet landscape light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(
    name = "Tablet landscape dark",
    widthDp = 1280,
    heightDp = 800,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun NowPlayingScreenPreview() {
    BitstreamTheme(albumColors = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val layout = when {
                    maxWidth >= 840.dp && maxHeight >= 600.dp -> NowPlayingLayout.TabletLandscape
                    maxHeight < 600.dp -> NowPlayingLayout.PhoneLandscape
                    maxWidth < 600.dp -> NowPlayingLayout.PhonePortrait
                    else -> NowPlayingLayout.TabletPortrait
                }
                NowPlayingContent(
                    state = previewNowPlayingState,
                    positionMs = 69_000,
                    durationMs = 243_000,
                    favoriteIds = setOf(1L),
                    upNext = previewUpNext,
                    layout = layout,
                    onBack = {},
                    onPlayPause = {},
                    onSeek = {},
                    onNext = {},
                    onPrevious = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onQueue = {},
                    onOutput = {},
                )
            }
        }
    }
}
