package dev.hien.bitstream.ui.search

import androidx.lifecycle.ViewModel
import dev.hien.bitstream.core.data.MusicRepository
import dev.hien.bitstream.core.model.Album
import dev.hien.bitstream.core.model.Artist
import dev.hien.bitstream.core.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val isSearching: Boolean = false,
)

class SearchViewModel(private val repository: MusicRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    /**
     * Suspending so the caller's coroutine (usually a `LaunchedEffect`) cancels
     * the previous keystroke's delay; that is the 250 ms debounce.
     */
    suspend fun query(query: String) {
        val trimmed = query.trim()
        mutableState.value = mutableState.value.copy(query = query)

        if (trimmed.isEmpty()) {
            mutableState.value = SearchUiState(query = query)
            return
        }

        mutableState.value = mutableState.value.copy(isSearching = true)
        delay(DEBOUNCE_MS)

        val results = repository.search(trimmed).first()
        mutableState.value = SearchUiState(
            query = query,
            results = results,
            albums = results
                .filter { it.album.isNotBlank() }
                .map { track ->
                    Album(
                        id = "${track.albumArtist.ifBlank { track.artist }}|${track.album}",
                        title = track.album,
                        albumArtist = track.albumArtist.ifBlank { track.artist },
                    )
                }
                .distinctBy { it.id },
            artists = results
                .map { it.artist }
                .filter { it.isNotBlank() }
                .distinct()
                .map { name -> Artist(id = name, name = name) },
        )
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
