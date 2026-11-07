package dev.hien.bitstream.ui.library

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.BitstreamEmptyState
import dev.hien.bitstream.core.designsystem.BitstreamLargeHeader
import dev.hien.bitstream.core.designsystem.BitstreamSearchPill
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.core.designsystem.BitstreamTile
import dev.hien.bitstream.core.designsystem.formatPlaybackTime
import dev.hien.bitstream.core.designsystem.formatRateDepth
import dev.hien.bitstream.core.designsystem.trackHue
import dev.hien.bitstream.core.designsystem.trackPalette
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
            onPlayAlbum = { albumId ->
                val albumTracks = state.tracks.filter { albumKey(it) == albumId }
                val first = albumTracks.firstOrNull()
                if (first != null) {
                    if (container.playbackEngine.state.value.track?.id == first.id) {
                        container.playbackEngine.play()
                    } else {
                        container.playTracks(albumTracks)
                    }
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

    val detailKey = navigator.currentDestination?.contentKey
    if (state.empty || detailKey == null) {
        // With nothing selected, the library owns the whole window so it can use
        // the mockup's wide home layout instead of a narrow list pane.
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
                            if (detailKey.startsWith(ARTIST_PREFIX)) {
                                ArtistScreen(
                                    artistId = detailKey.removePrefix(ARTIST_PREFIX),
                                    onTrack = onTrack,
                                    onBack = { scope.launch { navigator.navigateBack() } },
                                )
                            } else {
                                AlbumScreen(
                                    albumId = detailKey.removePrefix(ALBUM_PREFIX),
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
    onPlayAlbum: (String) -> Unit = {},
    onTrack: (Long) -> Unit = {},
    onArtist: (String) -> Unit = {},
    onPickFolder: () -> Unit = {},
    onSearch: () -> Unit = {},
    onFavorites: () -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    if (state.empty && !state.isScanning) {
        EmptyLibrary(onPickFolder = onPickFolder, onSettings = onSettings)
        return
    }

    var showArtists by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val short = maxHeight < 600.dp
        val onArtists = { showArtists = true }
        when {
            compact -> CompactLibrary(state, onAlbum, onPlayAlbum, onTrack, onSearch, onFavorites, onArtists, onShuffleAll, onSettings)
            short -> LandscapeLibrary(state, onAlbum, onPlayAlbum, onTrack, onSearch, onFavorites, onArtists, onShuffleAll, onSettings)
            maxWidth >= 840.dp -> ExpandedLibrary(state, onAlbum, onPlayAlbum, onTrack, onSearch, onFavorites, onArtists, onShuffleAll, onSettings)
            else -> PortraitLibrary(state, onAlbum, onPlayAlbum, onTrack, onSearch, onFavorites, onArtists, onShuffleAll, onSettings)
        }
    }

    ArtistsSheet(showArtists, state, onArtist = onArtist, onDismiss = { showArtists = false })
}

private enum class LibraryAction { Shuffle, Favorites, Artists, Folders }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val shortcuts = listOf(
    Triple("Shuffle all", LibraryAction.Shuffle, MaterialShapes.Cookie6Sided),
    Triple("Favorites", LibraryAction.Favorites, MaterialShapes.Cookie7Sided),
    Triple("Artists", LibraryAction.Artists, MaterialShapes.Cookie9Sided),
    Triple("Folders", LibraryAction.Folders, MaterialShapes.Cookie12Sided),
)

private val phoneSection = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight(650))
private val phoneLandscapeCardTitle = TextStyle(
    fontSize = 19.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight(700),
    letterSpacing = (-0.2).sp,
)
private val tabletCardTitle = TextStyle(
    fontSize = 24.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight(700),
    letterSpacing = (-0.2).sp,
)
private val portraitCardTitle = TextStyle(
    fontSize = 22.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight(700),
    letterSpacing = (-0.2).sp,
)
private val portraitTableCellTitle = TextStyle(
    fontSize = 28.sp,
    lineHeight = 34.sp,
    fontWeight = FontWeight(700),
    letterSpacing = (-0.3).sp,
)
private val tabletLabel = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight(600))
private val tabletRowTitle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight(600))

private fun Album.hue(): Int = trackHue(albumArtist, title, albumArtist, title)

private fun Album.artSeed(): Long = id.hashCode().toLong()

@Composable
private fun CompactLibrary(
    state: LibraryUiState,
    onAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit,
    onTrack: (Long) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onArtists: () -> Unit,
    onShuffleAll: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        ) {
            if (state.isScanning) {
                item { ScanningRow(Modifier.padding(horizontal = 24.dp)) }
            }
            item {
                LibraryHeader(
                    onSettings = onSettings,
                    onSearch = onSearch,
                    stacked = true,
                    titleStyle = MaterialTheme.typography.displaySmall,
                )
            }
            if (state.isScanning) {
                item { ScanningRow() }
            }
            if (state.albums.isNotEmpty()) {
                item { SectionTitle("Jump back in", MaterialTheme.typography.titleLarge, 24.dp, 12.dp) }
                item {
                    AlbumCarousel(
                        albums = state.albums,
                        preferredItemWidth = 236.dp,
                        itemSpacing = 12.dp,
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        height = 272.dp,
                        radius = 32.dp,
                        blobSize = 250.dp,
                        blobOffset = 60.dp,
                        cardPadding = 20.dp,
                        titleStyle = portraitCardTitle,
                        artistStyle = MaterialTheme.typography.titleSmall,
                        playSize = 52.dp,
                        playIcon = 28.dp,
                        onAlbum = onAlbum,
                        onPlayAlbum = onPlayAlbum,
                    )
                }
            }
            item {
                ShortcutGrid(
                    onShuffleAll = onShuffleAll,
                    onFavorites = onFavorites,
                    onArtists = onArtists,
                    onFolders = onSettings,
                    columns = 2,
                    height = 80.dp,
                    radius = 28.dp,
                    blobSize = 52.dp,
                    iconSize = 24.dp,
                    labelStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
                )
            }
            if (state.tracks.isNotEmpty()) {
                item { SectionTitle("Recently added", MaterialTheme.typography.titleLarge, 28.dp, 8.dp) }
                items(state.tracks.take(MAX_LIBRARY_TRACKS), key = { "track-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { onTrack(track.id) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        height = 64.dp,
                        radius = 24.dp,
                        leading = {
                            CoverArt(
                                artworkUri = track.artworkUri,
                                seed = track.id,
                                modifier = Modifier.size(46.dp),
                            )
                        },
                        trailing = {
                            DurationText(track.durationMs, MaterialTheme.typography.bodyMedium)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeLibrary(
    state: LibraryUiState,
    onAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit,
    onTrack: (Long) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onArtists: () -> Unit,
    onShuffleAll: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        LibraryHeader(
            onSettings = onSettings,
            onSearch = onSearch,
            stacked = false,
            titleStyle = MaterialTheme.typography.headlineLarge,
            height = 52.dp,
            searchHeight = 52.dp,
            searchIcon = 24.dp,
            searchPadding = 18.dp,
            searchGap = 14.dp,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp),
        ) {
            if (state.isScanning) {
                item { ScanningRow() }
            }
            item { SectionTitle("Jump back in", phoneSection, 8.dp, 8.dp, startPadding = 0.dp) }
            item {
                AlbumCarousel(
                    albums = state.albums,
                    preferredItemWidth = 216.dp,
                    itemSpacing = 12.dp,
                    height = 150.dp,
                    radius = 28.dp,
                    blobSize = 156.dp,
                    blobOffset = 44.dp,
                    cardPadding = 16.dp,
                    titleStyle = phoneLandscapeCardTitle,
                    titleMaxWidth = 140.dp,
                    artistStyle = MaterialTheme.typography.bodyMedium,
                    playSize = 44.dp,
                    playIcon = 24.dp,
                    onAlbum = onAlbum,
                    onPlayAlbum = onPlayAlbum,
                )
            }
            item {
                ShortcutGrid(
                    onShuffleAll = onShuffleAll,
                    onFavorites = onFavorites,
                    onArtists = onArtists,
                    onFolders = onSettings,
                    columns = 4,
                    height = 72.dp,
                    radius = 26.dp,
                    blobSize = 46.dp,
                    iconSize = 22.dp,
                    labelStyle = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            if (state.tracks.isNotEmpty()) {
                item { SectionTitle("Recently added", phoneSection, 24.dp, 6.dp, startPadding = 0.dp) }
                items(state.tracks.take(MAX_LIBRARY_TRACKS), key = { "track-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { onTrack(track.id) },
                        height = 60.dp,
                        radius = 22.dp,
                        startPadding = 4.dp,
                        endPadding = 12.dp,
                        leading = {
                            CoverArt(
                                artworkUri = track.artworkUri,
                                seed = track.id,
                                modifier = Modifier.size(44.dp),
                            )
                        },
                        trailing = {
                            DurationText(track.durationMs, MaterialTheme.typography.bodyMedium)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitLibrary(
    state: LibraryUiState,
    onAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit,
    onTrack: (Long) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onArtists: () -> Unit,
    onShuffleAll: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 24.dp, top = 32.dp, bottom = 0.dp),
    ) {
        LibraryHeader(
            onSettings = onSettings,
            onSearch = onSearch,
            stacked = false,
            titleStyle = MaterialTheme.typography.displayLarge,
            height = 64.dp,
            searchHeight = 60.dp,
            searchIcon = 26.dp,
            searchPadding = 22.dp,
            searchGap = 14.dp,
            searchText = MaterialTheme.typography.bodyLarge,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        ) {
            if (state.isScanning) {
                item { ScanningRow() }
            }
            item { SectionTitle("Jump back in", tabletSection, 0.dp, 12.dp, startPadding = 0.dp) }
            item {
                AlbumCarousel(
                    albums = state.albums,
                    preferredItemWidth = 300.dp,
                    itemSpacing = 16.dp,
                    height = 330.dp,
                    radius = 40.dp,
                    blobSize = 320.dp,
                    blobOffset = 70.dp,
                    cardPadding = 28.dp,
                    titleStyle = portraitTableCellTitle,
                    titleMaxWidth = 220.dp,
                    artistStyle = MaterialTheme.typography.titleMedium,
                    playSize = 60.dp,
                    playIcon = 32.dp,
                    onAlbum = onAlbum,
                    onPlayAlbum = onPlayAlbum,
                )
            }
            item { SectionTitle("Browse", tabletSection, 32.dp, 12.dp, startPadding = 0.dp) }
            item {
                ShortcutGrid(
                    onShuffleAll = onShuffleAll,
                    onFavorites = onFavorites,
                    onArtists = onArtists,
                    onFolders = onSettings,
                    columns = 2,
                    height = 88.dp,
                    radius = 32.dp,
                    blobSize = 56.dp,
                    iconSize = 26.dp,
                    labelStyle = tabletLabel,
                )
            }
            if (state.tracks.isNotEmpty()) {
                item { SectionTitle("Recently added", tabletSection, 32.dp, 8.dp, startPadding = 0.dp) }
                items(state.tracks.take(MAX_LIBRARY_TRACKS), key = { "track-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { onTrack(track.id) },
                        height = 72.dp,
                        radius = 28.dp,
                        startPadding = 8.dp,
                        endPadding = 16.dp,
                        gap = 16.dp,
                        titleStyle = MaterialTheme.typography.titleMedium,
                        leading = {
                            CoverArt(
                                artworkUri = track.artworkUri,
                                seed = track.id,
                                modifier = Modifier.size(54.dp),
                            )
                        },
                        trailing = {
                            DurationText(track.durationMs, MaterialTheme.typography.labelLarge)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedLibrary(
    state: LibraryUiState,
    onAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit,
    onTrack: (Long) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onArtists: () -> Unit,
    onShuffleAll: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 32.dp, top = 28.dp, bottom = 0.dp),
    ) {
        LibraryHeader(
            onSettings = onSettings,
            onSearch = onSearch,
            stacked = false,
            titleStyle = MaterialTheme.typography.displayLarge,
            height = 64.dp,
            searchHeight = 60.dp,
            searchIcon = 26.dp,
            searchPadding = 24.dp,
            searchGap = 16.dp,
            searchWidth = 520.dp,
            searchText = MaterialTheme.typography.bodyLarge,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 16.dp),
        ) {
            if (state.isScanning) {
                item { ScanningRow() }
            }
            item { SectionTitle("Jump back in", tabletSection, 0.dp, 12.dp, startPadding = 0.dp) }
            item {
                AlbumCarousel(
                    albums = state.albums,
                    preferredItemWidth = 300.dp,
                    itemSpacing = 16.dp,
                    height = 236.dp,
                    radius = 36.dp,
                    blobSize = 250.dp,
                    blobOffset = 64.dp,
                    cardPadding = 24.dp,
                    titleStyle = tabletCardTitle,
                    titleMaxWidth = 200.dp,
                    artistStyle = MaterialTheme.typography.bodyLarge,
                    playSize = 56.dp,
                    playIcon = 30.dp,
                    onAlbum = onAlbum,
                    onPlayAlbum = onPlayAlbum,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    Column(modifier = Modifier.width(456.dp)) {
                        SectionTitle("Browse", tabletSection, 0.dp, 12.dp, startPadding = 0.dp)
                        ShortcutGrid(
                            onShuffleAll = onShuffleAll,
                            onFavorites = onFavorites,
                            onArtists = onArtists,
                            onFolders = onSettings,
                            columns = 2,
                            height = 88.dp,
                            radius = 32.dp,
                            blobSize = 56.dp,
                            iconSize = 26.dp,
                            labelStyle = tabletLabel,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle("Recently added", tabletSection, 0.dp, 8.dp, startPadding = 0.dp)
                        state.tracks.take(MAX_RECENT_TRACKS).forEach { track ->
                            TrackRow(
                                track = track,
                                onClick = { onTrack(track.id) },
                                height = 68.dp,
                                radius = 26.dp,
                                startPadding = 8.dp,
                                endPadding = 16.dp,
                                gap = 16.dp,
                                titleStyle = tabletRowTitle,
                                leading = {
                                    CoverArt(
                                        artworkUri = track.artworkUri,
                                        seed = track.id,
                                        modifier = Modifier.size(50.dp),
                                    )
                                },
                                trailing = {
                                    DurationText(track.durationMs, MaterialTheme.typography.labelLarge)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val tabletSection = TextStyle(
    fontSize = 24.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight(650),
)

@Composable
private fun LibraryHeader(
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    stacked: Boolean,
    titleStyle: TextStyle,
    height: Dp = 56.dp,
    searchHeight: Dp = 56.dp,
    searchIcon: Dp = 24.dp,
    searchPadding: Dp = 20.dp,
    searchGap: Dp = 16.dp,
    searchWidth: Dp? = null,
    searchText: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val more: @Composable () -> Unit = {
        IconButton(onClick = onSettings) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "More options",
            )
        }
    }
    val searchModifier = if (stacked) {
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
    } else if (searchWidth != null) {
        Modifier.width(searchWidth)
    } else {
        Modifier
    }

    if (stacked) {
        Column {
            BitstreamLargeHeader(title = "Library", style = titleStyle, height = height) { more() }
            BitstreamSearchPill(
                text = "Search your music",
                painter = painterResource(R.drawable.ic_search),
                onClick = onSearch,
                modifier = searchModifier,
                height = searchHeight,
                iconSize = searchIcon,
                contentPadding = searchPadding,
                gap = searchGap,
                textStyle = searchText,
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (searchWidth != null) 32.dp else 16.dp),
        ) {
            Text(
                text = "Library",
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BitstreamSearchPill(
                text = "Search your music",
                painter = painterResource(R.drawable.ic_search),
                onClick = onSearch,
                modifier = if (searchWidth == null) Modifier.weight(1f) else searchModifier,
                height = searchHeight,
                iconSize = searchIcon,
                contentPadding = searchPadding,
                gap = searchGap,
                textStyle = searchText,
            )
            if (searchWidth != null) {
                Spacer(modifier = Modifier.weight(1f))
            }
            more()
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    style: TextStyle,
    top: Dp,
    bottom: Dp,
    startPadding: Dp = 24.dp,
) {
    Text(
        text = title,
        style = style,
        modifier = Modifier.padding(start = startPadding, end = 24.dp, top = top, bottom = bottom),
    )
}

@Composable
private fun ScanningRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Scanning library…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ShortcutGrid(
    onShuffleAll: () -> Unit,
    onFavorites: () -> Unit,
    onArtists: () -> Unit,
    onFolders: () -> Unit,
    columns: Int,
    height: Dp,
    radius: Dp,
    blobSize: Dp,
    iconSize: Dp,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val actions = mapOf(
        LibraryAction.Shuffle to onShuffleAll,
        LibraryAction.Favorites to onFavorites,
        LibraryAction.Artists to onArtists,
        LibraryAction.Folders to onFolders,
    )
    val painters = mapOf(
        LibraryAction.Shuffle to R.drawable.ic_shuffle,
        LibraryAction.Favorites to R.drawable.ic_favorite,
        LibraryAction.Artists to R.drawable.ic_person,
        LibraryAction.Folders to R.drawable.ic_folder,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (columns == 2) 10.dp else 10.dp),
    ) {
        shortcuts.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { (label, action, shape) ->
                    BitstreamTile(
                        label = label,
                        painter = painterResource(painters.getValue(action)),
                        onClick = actions.getValue(action),
                        modifier = Modifier.weight(1f),
                        shape = shape,
                        height = height,
                        radius = radius,
                        blobSize = blobSize,
                        iconSize = iconSize,
                        labelStyle = labelStyle,
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumCarousel(
    albums: List<Album>,
    preferredItemWidth: Dp,
    itemSpacing: Dp,
    height: Dp,
    radius: Dp,
    blobSize: Dp,
    blobOffset: Dp,
    cardPadding: Dp,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    playSize: Dp,
    playIcon: Dp,
    onAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    titleMaxWidth: Dp? = null,
) {
    val carouselState = rememberCarouselState { albums.size }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = preferredItemWidth,
        modifier = Modifier.fillMaxWidth(),
        itemSpacing = itemSpacing,
        contentPadding = contentPadding,
    ) { index ->
        val album = albums[index]
        AlbumCard(
            album = album,
            height = height,
            radius = radius,
            blobSize = blobSize,
            blobOffset = blobOffset,
            contentPadding = cardPadding,
            titleStyle = titleStyle,
            titleMaxWidth = titleMaxWidth,
            artistStyle = artistStyle,
            playSize = playSize,
            playIcon = playIcon,
            onClick = { onAlbum(album.id) },
            onPlay = { onPlayAlbum(album.id) },
            maskModifier = { Modifier.maskClip(RoundedCornerShape(radius)) },
        )
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    height: Dp,
    radius: Dp,
    blobSize: Dp,
    blobOffset: Dp,
    contentPadding: Dp,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    playSize: Dp,
    playIcon: Dp,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    titleMaxWidth: Dp? = null,
    // Built by the caller inside CarouselItemScope so the card carries the
    // carousel's rounded compression mask; identity elsewhere.
    maskModifier: @Composable () -> Modifier = { Modifier },
) {
    val hue = album.hue()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = remember(hue, dark) { trackPalette(hue, dark) }
    Surface(
        onClick = onClick,
        // Fill the slot: the multi-browse carousel compresses peeking items, and
        // maskModifier keeps their clipped edges rounded instead of straight.
        modifier = Modifier
            .then(maskModifier())
            .fillMaxSize(),
        shape = RoundedCornerShape(radius),
        color = Color(palette.primaryContainer),
        contentColor = Color(palette.onPrimaryContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(radius)),
        ) {
            CoverArt(
                artworkUri = album.artworkUri,
                seed = album.artSeed(),
                hue = hue,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = blobOffset, y = blobOffset)
                    .size(blobSize),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                val titleModifier = if (titleMaxWidth != null) {
                    Modifier.widthIn(max = titleMaxWidth)
                } else {
                    Modifier
                }
                Text(
                    text = album.title,
                    style = titleStyle,
                    modifier = titleModifier,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.albumArtist,
                    style = artistStyle,
                    modifier = titleModifier,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(contentPadding)
                    .size(maxOf(playSize, 48.dp))
                    .semantics { contentDescription = "Play ${album.title}" }
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(playSize),
                    shape = CircleShape,
                    color = Color(palette.onPrimaryContainer),
                    contentColor = Color(palette.primaryContainer),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = null,
                            modifier = Modifier.size(playIcon),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationText(durationMs: Long, style: TextStyle) {
    val duration = formatPlaybackTime(durationMs)
    if (duration.isNotBlank()) {
        Text(duration, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val previewAlbums = listOf(
    Album(id = "album-1", title = "Long Way Home", albumArtist = "Neon Meridian", trackCount = 10),
    Album(id = "album-2", title = "Glass Harbor", albumArtist = "Low Tide Choir", trackCount = 8),
    Album(id = "album-3", title = "Paper Satellites", albumArtist = "Aster Field", trackCount = 12),
)

private val previewTracks = listOf(
    Track(
        id = 1L,
        uri = "content://preview/1",
        title = "Long Way Home",
        artist = "Neon Meridian",
        album = "Long Way Home",
        durationMs = 214_000,
    ),
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
)

private val previewState = LibraryUiState(albums = previewAlbums, tracks = previewTracks)

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
internal fun LibraryScreenPreview() {
    BitstreamTheme(albumColors = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LibraryScreen(state = previewState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistsSheet(
    visible: Boolean,
    state: LibraryUiState,
    onArtist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                        onDismiss()
                        onArtist(artist.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onPickFolder: () -> Unit, onSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        BitstreamLargeHeader(title = "Library") {
            IconButton(onClick = onSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "More options",
                )
            }
        }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
        )
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
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    height: Dp = 64.dp,
    radius: Dp = 24.dp,
    startPadding: Dp = 12.dp,
    endPadding: Dp = 12.dp,
    gap: Dp = 14.dp,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    selected: Boolean = false,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(containerColor)
            .then(
                if (selected) {
                    Modifier.semantics { this.selected = true }
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(start = startPadding, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = titleStyle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = track.artist.ifBlank { track.album }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            val format = formatRateDepth(track.bitDepth, track.sampleRate)
            if (format.isNotBlank()) {
                Text(
                    text = format,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                )
            }
        }
    }
}

private const val MAX_LIBRARY_TRACKS = 100
private const val MAX_RECENT_TRACKS = 8
