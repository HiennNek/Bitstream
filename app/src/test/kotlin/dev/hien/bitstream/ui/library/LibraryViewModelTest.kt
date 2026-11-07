package dev.hien.bitstream.ui.library

import dev.hien.bitstream.core.data.MusicRepository
import dev.hien.bitstream.core.model.Album
import dev.hien.bitstream.core.model.Artist
import dev.hien.bitstream.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
class LibraryViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyLibraryExposesEmptyState() = runTest {
        val viewModel = LibraryViewModel(FakeMusicRepository())

        assertTrue(viewModel.state.first().empty)
    }

    @Test
    fun albumsAreSortedByAlbumArtistThenTitle() = runTest {
        val viewModel = LibraryViewModel(
            FakeMusicRepository(
                albums = listOf(
                    album(title = "B", albumArtist = "Zed"),
                    album(title = "A", albumArtist = "Amy"),
                    album(title = "A", albumArtist = "Zed"),
                ),
            ),
        )

        assertEquals(
            listOf("A", "A", "B"),
            viewModel.state.first { it.albums.isNotEmpty() }.albums.map { it.title },
        )
        assertEquals(
            listOf("Amy", "Zed", "Zed"),
            viewModel.state.first { it.albums.isNotEmpty() }.albums.map { it.albumArtist },
        )
    }

    @Test
    fun tracksAreSortedByDiscThenTrackNumber() = runTest {
        val viewModel = LibraryViewModel(
            FakeMusicRepository(
                tracks = listOf(
                    track(id = 1, disc = 2, number = 1, title = "Second disc"),
                    track(id = 2, disc = 1, number = 2, title = "Track two"),
                    track(id = 3, disc = 1, number = 1, title = "Track one"),
                ),
            ),
        )

        assertEquals(
            listOf("Track one", "Track two", "Second disc"),
            viewModel.state.first { it.tracks.isNotEmpty() }.tracks.map { it.title },
        )
    }

    @Test
    fun scanningFlagIsExposed() = runTest {
        val viewModel = LibraryViewModel(FakeMusicRepository(), scanning = flowOf(true))

        assertTrue(viewModel.state.first { it.isScanning }.isScanning)
    }

    @Test
    fun recentTracksFlowThroughUnsorted() = runTest {
        val viewModel = LibraryViewModel(
            FakeMusicRepository(recentTracks = listOf(track(id = 9, disc = 1, number = 1, title = "Latest"))),
        )

        assertEquals(
            listOf("Latest"),
            viewModel.state.first { it.recentTracks.isNotEmpty() }.recentTracks.map { it.title },
        )
    }
}

private class FakeMusicRepository(
    albums: List<Album> = emptyList(),
    tracks: List<Track> = emptyList(),
    artists: List<Artist> = emptyList(),
    recentTracks: List<Track> = emptyList(),
) : MusicRepository {
    private val albumList = albums
    private val trackList = tracks
    private val artistList = artists

    override val tracks: Flow<List<Track>> = flowOf(trackList)
    override val albums: Flow<List<Album>> = flowOf(albumList)
    override val artists: Flow<List<Artist>> = flowOf(artistList)
    override val favoriteTracks: Flow<List<Track>> = flowOf(emptyList())
    override val favoriteIds: Flow<Set<Long>> = flowOf(emptySet())
    override val recentTracks: Flow<List<Track>> = flowOf(recentTracks)

    override fun search(query: String): Flow<List<Track>> = flowOf(emptyList())

    override suspend fun tracksByIds(ids: List<Long>): List<Track> =
        trackList.filter { it.id in ids }

    override suspend fun setFavorite(trackId: Long, favorite: Boolean) = Unit
    override suspend fun recordPlay(trackId: Long) = Unit

    override suspend fun rebuildDerived() = Unit
}

private fun album(title: String, albumArtist: String): Album = Album(
    id = "$albumArtist|$title",
    title = title,
    albumArtist = albumArtist,
)

private fun track(
    id: Long,
    disc: Int,
    number: Int,
    title: String,
): Track = Track(
    id = id,
    uri = "content://test/$id.flac",
    title = title,
    discNumber = disc,
    trackNumber = number,
)
