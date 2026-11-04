package dev.hien.bitstream.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hien.bitstream.core.data.MusicRepository
import dev.hien.bitstream.core.data.db.PlaylistDao
import dev.hien.bitstream.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PlaylistInfo(val id: Long, val name: String)

data class PlaylistsUiState(
    val playlists: List<PlaylistInfo> = emptyList(),
    val selectedId: Long? = null,
    val entries: List<Long> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

/** The playlist operations the UI needs, kept separate so it can be faked in tests. */
interface PlaylistStore {
    val playlists: Flow<List<PlaylistInfo>>

    fun entries(playlistId: Long): Flow<List<Long>>

    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun delete(id: Long)

    suspend fun addTrack(playlistId: Long, trackId: Long)

    suspend fun removeTrack(playlistId: Long, trackId: Long)

    suspend fun reorder(playlistId: Long, from: Int, to: Int)
}

class RoomPlaylistStore(private val dao: PlaylistDao) : PlaylistStore {
    override val playlists: Flow<List<PlaylistInfo>> =
        dao.observePlaylists().map { entities ->
            entities.map { PlaylistInfo(id = it.id, name = it.name) }
        }

    override fun entries(playlistId: Long): Flow<List<Long>> =
        dao.observeEntries(playlistId).map { entries -> entries.map { it.trackId } }

    override suspend fun create(name: String): Long = dao.create(name)

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun addTrack(playlistId: Long, trackId: Long) =
        dao.addTrack(playlistId, trackId)

    override suspend fun removeTrack(playlistId: Long, trackId: Long) {
        val entry = dao.entries(playlistId).firstOrNull { it.trackId == trackId } ?: return
        dao.removeEntry(entry.id)
    }

    override suspend fun reorder(playlistId: Long, from: Int, to: Int) =
        dao.reorder(playlistId, from, to)
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModel(
    private val store: PlaylistStore,
    repository: MusicRepository? = null,
) : ViewModel() {
    private val requestedSelection = MutableStateFlow<Long?>(null)

    private val effectiveSelection: Flow<Long?> = combine(
        requestedSelection,
        store.playlists,
    ) { requested, playlists ->
        requested?.takeIf { id -> playlists.any { it.id == id } }
            ?: playlists.firstOrNull()?.id
    }

    private val entries: Flow<List<Long>> = effectiveSelection.flatMapLatest { playlistId ->
        if (playlistId == null) flowOf(emptyList()) else store.entries(playlistId)
    }

    private val allTracks: Flow<List<Track>> = repository?.tracks ?: flowOf(emptyList())

    val state: StateFlow<PlaylistsUiState> = combine(
        store.playlists,
        requestedSelection,
        entries,
        allTracks,
    ) { playlists, selectedId, entryIds, tracks ->
        PlaylistsUiState(
            playlists = playlists,
            selectedId = selectedId,
            entries = entryIds,
            tracks = entryIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlaylistsUiState(),
    )

    fun select(playlistId: Long?) {
        requestedSelection.value = playlistId
    }

    suspend fun create(name: String): Long = store.create(name.trim())

    suspend fun rename(id: Long, name: String) = store.rename(id, name.trim())

    suspend fun delete(id: Long) {
        store.delete(id)
        if (requestedSelection.value == id) {
            requestedSelection.value = null
        }
    }

    suspend fun addTrack(playlistId: Long, trackId: Long) =
        store.addTrack(playlistId, trackId)

    suspend fun removeTrack(playlistId: Long, trackId: Long) =
        store.removeTrack(playlistId, trackId)

    suspend fun reorder(playlistId: Long, from: Int, to: Int) =
        store.reorder(playlistId, from, to)
}
