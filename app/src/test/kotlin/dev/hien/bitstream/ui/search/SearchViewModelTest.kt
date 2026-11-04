package dev.hien.bitstream.ui.search

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
class SearchViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun blankQueryClearsResults() = runTest {
        val viewModel = SearchViewModel(FakeMusicRepository(listOf(track("Blue Train"))))

        viewModel.query("   ")

        assertTrue(viewModel.state.first().results.isEmpty())
    }

    @Test
    fun searchIsCaseInsensitive() = runTest {
        val viewModel = SearchViewModel(FakeMusicRepository(listOf(track("Blue Train"))))

        viewModel.query("BLUE")

        assertEquals(1, viewModel.state.first().results.size)
    }

    @Test
    fun resultsAreGroupedByAlbumAndArtist() = runTest {
        val viewModel = SearchViewModel(
            FakeMusicRepository(
                listOf(
                    track("Blue Train", artist = "Coltrane", album = "Blue Train"),
                    track("Blue in Green", artist = "Davis", album = "Kind of Blue"),
                ),
            ),
        )

        viewModel.query("blue")

        val state = viewModel.state.first()
        assertEquals(2, state.results.size)
        assertEquals(setOf("Blue Train", "Kind of Blue"), state.albums.map { it.title }.toSet())
        assertEquals(setOf("Coltrane", "Davis"), state.artists.map { it.name }.toSet())
    }
}

private class FakeMusicRepository(private val trackList: List<Track>) : MusicRepository {
    override val tracks: Flow<List<Track>> = flowOf(trackList)
    override val albums: Flow<List<Album>> = flowOf(emptyList())
    override val artists: Flow<List<Artist>> = flowOf(emptyList())

    override fun search(query: String): Flow<List<Track>> = flowOf(
        trackList.filter { track ->
            listOf(track.title, track.artist, track.album)
                .any { it.contains(query, ignoreCase = true) }
        },
    )

    override suspend fun tracksByIds(ids: List<Long>): List<Track> =
        trackList.filter { it.id in ids }

    override suspend fun rebuildDerived() = Unit
}

private fun track(
    title: String,
    artist: String = "Artist",
    album: String = "Album",
): Track = Track(
    id = title.hashCode().toLong(),
    uri = "content://test/${title.hashCode()}.flac",
    title = title,
    artist = artist,
    album = album,
    albumArtist = artist,
)
