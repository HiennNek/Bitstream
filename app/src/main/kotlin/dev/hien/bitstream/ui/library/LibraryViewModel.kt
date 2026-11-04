package dev.hien.bitstream.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hien.bitstream.core.data.MusicRepository
import dev.hien.bitstream.core.model.Album
import dev.hien.bitstream.core.model.Artist
import dev.hien.bitstream.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isScanning: Boolean = false,
) {
    val empty: Boolean
        get() = albums.isEmpty() && tracks.isEmpty()
}

class LibraryViewModel(
    repository: MusicRepository,
    scanning: Flow<Boolean> = flowOf(false),
) : ViewModel() {
    val state: StateFlow<LibraryUiState> = combine(
        repository.albums,
        repository.artists,
        repository.tracks,
        scanning,
    ) { albums, artists, tracks, isScanning ->
        LibraryUiState(
            albums = albums.sortedWith(
                compareBy<Album>(
                    { it.albumArtist.lowercase() },
                    { it.year ?: 0 },
                    { it.title.lowercase() },
                ),
            ),
            artists = artists.sortedBy { it.name.lowercase() },
            tracks = tracks.sortedWith(
                compareBy<Track>(
                    { it.discNumber },
                    { it.trackNumber },
                    { it.title.lowercase() },
                ),
            ),
            isScanning = isScanning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LibraryUiState(),
    )
}
