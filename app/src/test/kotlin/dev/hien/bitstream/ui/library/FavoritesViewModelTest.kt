package dev.hien.bitstream.ui.library

import dev.hien.bitstream.core.data.MusicRepository
import dev.hien.bitstream.core.model.Album
import dev.hien.bitstream.core.model.Artist
import dev.hien.bitstream.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleMarksAnUnfavoritedTrackAsFavorite() = runTest {
        val repository = FakeFavoritesRepository(listOf(track(7, "Blue Train")))
        val viewModel = FavoritesViewModel(repository)

        viewModel.toggle(7)

        assertEquals(setOf(7L), repository.favoriteIdsFlow.value)
        assertEquals(setOf(7L), viewModel.state.first().favoriteIds)
        assertEquals(listOf(7L), viewModel.state.first().tracks.map { it.id })
    }

    @Test
    fun toggleRemovesAnAlreadyFavoritedTrack() = runTest {
        val repository = FakeFavoritesRepository(listOf(track(7, "Blue Train")))
        repository.favoriteIdsFlow.value = setOf(7L)
        val viewModel = FavoritesViewModel(repository)

        viewModel.toggle(7)

        assertEquals(emptySet<Long>(), repository.favoriteIdsFlow.value)
    }
}

private class FakeFavoritesRepository(private val allTracks: List<Track>) : MusicRepository {
    val favoriteIdsFlow = MutableStateFlow<Set<Long>>(emptySet())

    override val tracks: Flow<List<Track>> = flowOf(allTracks)
    override val albums: Flow<List<Album>> = flowOf(emptyList())
    override val artists: Flow<List<Artist>> = flowOf(emptyList())
    override val favoriteTracks: Flow<List<Track>> =
        favoriteIdsFlow.map { ids -> allTracks.filter { it.id in ids } }
    override val favoriteIds: Flow<Set<Long>> = favoriteIdsFlow

    override fun search(query: String): Flow<List<Track>> = flowOf(emptyList())

    override suspend fun tracksByIds(ids: List<Long>): List<Track> =
        allTracks.filter { it.id in ids }

    override suspend fun setFavorite(trackId: Long, favorite: Boolean) {
        favoriteIdsFlow.value = if (favorite) {
            favoriteIdsFlow.value + trackId
        } else {
            favoriteIdsFlow.value - trackId
        }
    }

    override suspend fun rebuildDerived() = Unit
}

private fun track(id: Long, title: String): Track = Track(
    id = id,
    uri = "content://test/$id.flac",
    title = title,
    artist = "Coltrane",
    album = "Blue Train",
    albumArtist = "Coltrane",
)
