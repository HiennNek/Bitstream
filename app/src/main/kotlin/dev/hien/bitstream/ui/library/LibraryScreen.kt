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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.hien.bitstream.core.designsystem.BitstreamSectionHeader
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.designsystem.formatRateDepth
import dev.hien.bitstream.core.model.Album
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.ui.components.CoverArt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LibraryRoute(onTrack: (Long) -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAlbum: (String) -> Unit = {},
    onTrack: (Long) -> Unit = {},
    onArtist: (String) -> Unit = {},
    onPickFolder: () -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
    ) { padding ->
        if (state.empty && !state.isScanning) {
            EmptyLibrary(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onPickFolder = onPickFolder,
            )
        } else {
            LibraryContent(
                state = state,
                onAlbum = onAlbum,
                onTrack = onTrack,
                onArtist = onArtist,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onAlbum: (String) -> Unit,
    onTrack: (Long) -> Unit,
    onArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val artistsRowState = rememberLazyListState()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (state.isScanning) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Scanning library…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.artists.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BitstreamSectionHeader("Artists", modifier = Modifier.padding(top = Spacing.sm))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    state = artistsRowState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.artists, key = { it.id }) { artist ->
                        AssistChip(
                            onClick = { onArtist(artist.id) },
                            label = { Text(artist.name, maxLines = 1) },
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            BitstreamSectionHeader("Albums", modifier = Modifier.padding(top = Spacing.sm))
        }
        items(state.albums, key = { "album-${it.id}" }) { album ->
            AlbumCard(album = album, onClick = { onAlbum(album.id) })
        }

        if (state.tracks.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BitstreamSectionHeader("Tracks", modifier = Modifier.padding(top = Spacing.sm))
            }
            items(
                items = state.tracks.take(MAX_LIBRARY_TRACKS),
                key = { "track-${it.id}" },
                span = { GridItemSpan(maxLineSpan) },
            ) { track ->
                TrackRow(track = track, onClick = { onTrack(track.id) })
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            AlbumArtwork(
                uri = album.artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Column(Modifier.padding(Spacing.md)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.albumArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
fun TrackRow(track: Track, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        content = {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            val format = formatRateDepth(track.bitDepth, track.sampleRate)
            if (format.isNotBlank()) {
                Text(format, style = MaterialTheme.typography.labelMedium)
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
