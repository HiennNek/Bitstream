package dev.hien.bitstream.ui.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.BitstreamEmptyState
import dev.hien.bitstream.core.designsystem.BitstreamLargeHeader
import dev.hien.bitstream.core.designsystem.BitstreamSearchPill
import dev.hien.bitstream.core.designsystem.BitstreamSectionHeader
import dev.hien.bitstream.core.designsystem.BitstreamTile
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.designsystem.formatPlaybackTime
import dev.hien.bitstream.core.designsystem.formatRateDepth
import dev.hien.bitstream.core.model.Album
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.ui.components.CoverArt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LibraryRoute(
    onTrack: (Long) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LibraryViewModel(container.musicRepository, container.scanning)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            scope.launch {
                container.addFolder(uri.toString(), uri.lastPathSegment ?: "Music")
                container.scanFolder(uri.toString())
            }
        }
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    val listContent: @Composable () -> Unit = {
        LibraryScreen(
            state = state,
            onAlbum = { albumId ->
                scope.launch {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, "album:$albumId")
                }
            },
            onTrack = onTrack,
            onArtist = { artist ->
                scope.launch {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, "artist:$artist")
                }
            },
            onPickFolder = { picker.launch(null) },
            onSearch = onSearch,
            onFavorites = onFavorites,
            onShuffleAll = { container.playTracks(state.tracks, shuffle = true) },
            onSettings = onSettings,
        )
    }

    if (state.empty) {
        // An empty library has nothing to put in a detail pane, so let the
        // empty state use the full window on tablets.
        listContent()
    } else {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = { AnimatedPane { listContent() } },
            detailPane = {
                AnimatedPane {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                            val key = navigator.currentDestination?.contentKey
                            when {
                                key == null -> DetailPlaceholder()
                                key.startsWith(ARTIST_PREFIX) -> ArtistScreen(
                                    artistId = key.removePrefix(ARTIST_PREFIX),
                                    onTrack = onTrack,
                                    onBack = { scope.launch { navigator.navigateBack() } },
                                )

                                else -> AlbumScreen(
                                    albumId = key.removePrefix(ALBUM_PREFIX),
                                    onTrack = onTrack,
                                    onBack = { scope.launch { navigator.navigateBack() } },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

private const val ALBUM_PREFIX = "album:"
private const val ARTIST_PREFIX = "artist:"

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAlbum: (String) -> Unit = {},
    onTrack: (Long) -> Unit = {},
    onArtist: (String) -> Unit = {},
    onPickFolder: () -> Unit = {},
    onSearch: () -> Unit = {},
    onFavorites: () -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BitstreamLargeHeader(title = "Library") {
            IconButton(onClick = onSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "More options",
                )
            }
        }
        if (state.empty && !state.isScanning) {
            EmptyLibrary(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                onPickFolder = onPickFolder,
            )
        } else {
            LibraryContent(
                state = state,
                onAlbum = onAlbum,
                onTrack = onTrack,
                onArtist = onArtist,
                onSearch = onSearch,
                onFavorites = onFavorites,
                onShuffleAll = onShuffleAll,
                onSettings = onSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onAlbum: (String) -> Unit,
    onTrack: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onShuffleAll: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showArtists by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Spacing.xxl,
            end = Spacing.xxl,
            top = Spacing.md,
            bottom = Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            BitstreamSearchPill(
                text = "Search your music",
                painter = painterResource(R.drawable.ic_search),
                onClick = onSearch,
            )
        }

        if (state.isScanning) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Scanning library…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.albums.isNotEmpty()) {
            item {
                BitstreamSectionHeader(
                    title = "Jump back in",
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(state.albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onAlbum(album.id) },
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    BitstreamTile(
                        label = "Shuffle all",
                        painter = painterResource(R.drawable.ic_shuffle),
                        onClick = onShuffleAll,
                        modifier = Modifier.weight(1f),
                    )
                    BitstreamTile(
                        label = "Favorites",
                        painter = painterResource(R.drawable.ic_favorite),
                        onClick = onFavorites,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    BitstreamTile(
                        label = "Artists",
                        painter = painterResource(R.drawable.ic_person),
                        onClick = { showArtists = true },
                        modifier = Modifier.weight(1f),
                    )
                    BitstreamTile(
                        label = "Folders",
                        painter = painterResource(R.drawable.ic_folder),
                        onClick = onSettings,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (state.tracks.isNotEmpty()) {
            item {
                BitstreamSectionHeader(
                    title = "Recently added",
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            items(
                items = state.tracks.take(MAX_LIBRARY_TRACKS),
                key = { "track-${it.id}" },
            ) { track ->
                TrackRow(
                    track = track,
                    onClick = { onTrack(track.id) },
                    leading = {
                        CoverArt(
                            artworkUri = track.artworkUri,
                            seed = track.id,
                            modifier = Modifier.size(46.dp),
                        )
                    },
                    trailing = {
                        val duration = formatPlaybackTime(track.durationMs)
                        if (duration.isNotBlank()) {
                            Text(duration, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                )
            }
        }
    }

    if (showArtists) {
        ModalBottomSheet(onDismissRequest = { showArtists = false }) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.artists, key = { it.id }) { artist ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = artist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.clickable {
                            showArtists = false
                            onArtist(artist.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BitstreamEmptyState(
            title = "Your library is empty",
            message = "Choose a folder of FLAC files. Bitstream reads the metadata only and never " +
                "touches the audio.",
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_library_music),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            actionLabel = "Choose folder",
            onAction = onPickFolder,
        )
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 236.dp, height = 272.dp),
        shape = RoundedCornerShape(Radii.hero),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CoverArt(
                artworkUri = album.artworkUri,
                seed = album.id.hashCode().toLong(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(250.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.albumArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumArtwork(uri: String?, modifier: Modifier = Modifier) {
    CoverArt(
        artworkUri = uri,
        seed = (uri ?: "").hashCode().toLong(),
        modifier = modifier,
    )
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.tile))
            .clickable(onClick = onClick),
        leadingContent = leading,
        content = {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val subtitle = listOf(track.artist, track.album)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            if (trailing != null) {
                trailing()
            } else {
                val format = formatRateDepth(track.bitDepth, track.sampleRate)
                if (format.isNotBlank()) {
                    Text(format, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
    )
}

@Composable
private fun DetailPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Select an album",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_LIBRARY_TRACKS = 100
