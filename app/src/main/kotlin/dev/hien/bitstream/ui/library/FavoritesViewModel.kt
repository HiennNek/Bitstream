package dev.hien.bitstream.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hien.bitstream.core.data.MusicRepository
import dev.hien.bitstream.core.model.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val tracks: List<Track> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
)

class FavoritesViewModel(
    private val repository: MusicRepository,
) : ViewModel() {
    val state: StateFlow<FavoritesUiState> = combine(
        repository.favoriteTracks,
        repository.favoriteIds,
    ) { tracks, ids ->
        FavoritesUiState(tracks = tracks, favoriteIds = ids)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FavoritesUiState(),
    )

    fun toggle(trackId: Long) {
        val favorite = trackId !in state.value.favoriteIds
        viewModelScope.launch { repository.setFavorite(trackId, favorite) }
    }
}
