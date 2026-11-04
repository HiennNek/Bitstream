package dev.hien.bitstream.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
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
    Scaffold(
        topBar = { TopAppBar(title = { Text("Search") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Tracks, albums, artists") },
            )

            when {
                query.isBlank() -> CenteredHint("Search your library by title, artist, or album.")
                state.isSearching && state.results.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.results.isEmpty() -> CenteredHint("No matches for \"$query\".")
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
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            items(state.albums, key = { "album-${it.id}" }) { album ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbum(album.id) },
                    content = {
                        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(album.albumArtist) },
                )
            }
        }

        if (state.artists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            item {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
            item { SectionHeader("Tracks") }
            items(state.results, key = { "track-${it.id}" }) { track ->
                TrackRow(track = track, onClick = { onTrack(track.id) })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CenteredHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}
