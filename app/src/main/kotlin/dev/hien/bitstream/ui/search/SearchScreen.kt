package dev.hien.bitstream.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.hien.bitstream.core.designsystem.BitstreamSectionHeader
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.ui.library.TrackRow

@Composable
fun SearchRoute(
    onTrack: (Long) -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(container.musicRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(query) {
        viewModel.query(query)
    }

    SearchScreen(
        state = state,
        query = query,
        onQueryChange = { query = it },
        onTrack = onTrack,
        onAlbum = onAlbum,
        onArtist = onArtist,
    )
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onTrack: (Long) -> Unit = {},
    onAlbum: (String) -> Unit = {},
    onArtist: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BitstreamLargeHeader(title = "Search")

        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text("Tracks, albums, artists") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                        )
                    },
                )
            },
            expanded = false,
            onExpandedChange = {},
            content = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                query.isBlank() -> BitstreamEmptyState(
                    title = "Search your library by title, artist, or album.",
                    modifier = Modifier.fillMaxSize().padding(Spacing.xxxl),
                )
                state.isSearching && state.results.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.results.isEmpty() -> BitstreamEmptyState(
                    title = "No matches for \"$query\".",
                    modifier = Modifier.fillMaxSize().padding(Spacing.xxxl),
                )
                else -> SearchResults(
                    state = state,
                    onTrack = onTrack,
                    onAlbum = onAlbum,
                    onArtist = onArtist,
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    onTrack: (Long) -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            bottom = Spacing.xxl,
        ),
    ) {
        if (state.albums.isNotEmpty()) {
            item { BitstreamSectionHeader("Albums", modifier = Modifier.padding(vertical = Spacing.sm)) }
            items(state.albums, key = { "album-${it.id}" }) { album ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.tile))
                        .clickable { onAlbum(album.id) },
                    content = {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = { Text(album.albumArtist) },
                )
            }
        }

        if (state.artists.isNotEmpty()) {
            item { BitstreamSectionHeader("Artists", modifier = Modifier.padding(vertical = Spacing.sm)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.artists, key = { it.id }) { artist ->
                        AssistChip(
                            onClick = { onArtist(artist.id) },
                            label = { Text(artist.name) },
                        )
                    }
                }
            }
        }

        if (state.results.isNotEmpty()) {
            item { BitstreamSectionHeader("Tracks", modifier = Modifier.padding(vertical = Spacing.sm)) }
            items(state.results, key = { "track-${it.id}" }) { track ->
                TrackRow(track = track, onClick = { onTrack(track.id) })
            }
        }
    }
}
