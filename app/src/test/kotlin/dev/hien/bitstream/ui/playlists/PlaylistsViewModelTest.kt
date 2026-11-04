package dev.hien.bitstream.ui.playlists

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reorderMovesEntryToNewPosition() = runTest {
        val viewModel = PlaylistsViewModel(FakePlaylistStore(listOf(1, 2, 3)))

        viewModel.reorder(playlistId = 1, from = 0, to = 2)

        assertEquals(listOf(2L, 3L, 1L), viewModel.state.first().entries)
    }

    @Test
    fun createAddsAPlaylist() = runTest {
        val viewModel = PlaylistsViewModel(FakePlaylistStore(emptyList()))

        viewModel.create("Road trip")

        assertTrue(viewModel.state.first().playlists.any { it.name == "Road trip" })
    }

    @Test
    fun addTrackAppendsToTheSelectedPlaylist() = runTest {
        val viewModel = PlaylistsViewModel(FakePlaylistStore(listOf(1)))

        viewModel.addTrack(playlistId = 1, trackId = 9)

        assertEquals(listOf(1L, 9L), viewModel.state.first().entries)
    }

    @Test
    fun removeTrackDropsTheEntry() = runTest {
        val viewModel = PlaylistsViewModel(FakePlaylistStore(listOf(1, 2, 3)))

        viewModel.removeTrack(playlistId = 1, trackId = 2)

        assertEquals(listOf(1L, 3L), viewModel.state.first().entries)
    }
}

private class FakePlaylistStore(initialEntries: List<Long>) : PlaylistStore {
    private val playlistsFlow = MutableStateFlow(listOf(PlaylistInfo(id = 1, name = "Mix")))
    private val entriesFlow = MutableStateFlow(initialEntries)
    private var nextId = 2L

    override val playlists: Flow<List<PlaylistInfo>> = playlistsFlow

    override fun entries(playlistId: Long): Flow<List<Long>> = entriesFlow

    override suspend fun create(name: String): Long {
        val id = nextId++
        playlistsFlow.update { it + PlaylistInfo(id = id, name = name) }
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        playlistsFlow.update { list ->
            list.map { if (it.id == id) it.copy(name = name) else it }
        }
    }

    override suspend fun delete(id: Long) {
        playlistsFlow.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun addTrack(playlistId: Long, trackId: Long) {
        entriesFlow.update { it + trackId }
    }

    override suspend fun removeTrack(playlistId: Long, trackId: Long) {
        entriesFlow.update { it - trackId }
    }

    override suspend fun reorder(playlistId: Long, from: Int, to: Int) {
        entriesFlow.update { list ->
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
}
