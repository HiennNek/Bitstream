package dev.hien.bitstream.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.hien.bitstream.core.data.db.BitstreamDatabase
import dev.hien.bitstream.core.data.db.TrackEntity
import dev.hien.bitstream.core.model.RepeatMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitstreamDatabaseTest {
    private lateinit var db: BitstreamDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BitstreamDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun albumQueryGroupsTracksAndKeepsAlbumArtist() = runBlocking {
        db.trackDao().upsertAll(
            listOf(
                track(id = 1, title = "One", album = "Record", albumArtist = "Artist"),
                track(id = 2, title = "Two", album = "Record", albumArtist = "Artist"),
            ),
        )
        db.albumDao().rebuild()

        val albums = db.albumDao().observeAlbums().first()

        assertEquals(1, albums.size)
        assertEquals("Record", albums.single().title)
        assertEquals("Artist", albums.single().albumArtist)
        assertEquals(2, albums.single().trackCount)
    }

    @Test
    fun searchMatchesTitleArtistAndAlbum() = runBlocking {
        db.trackDao().upsertAll(
            listOf(
                track(
                    id = 1,
                    title = "Blue Train",
                    artist = "Coltrane",
                    album = "Blue Train",
                ),
            ),
        )

        assertEquals(1, db.trackDao().search("colt").first().size)
        assertEquals(1, db.trackDao().search("blue").first().size)
    }

    @Test
    fun searchIsCaseInsensitiveAndPrefixBased() = runBlocking {
        db.trackDao().upsertAll(
            listOf(track(id = 1, title = "Blue Train", artist = "Coltrane")),
        )

        assertEquals(1, db.trackDao().search("BLUE").first().size)
        assertEquals(1, db.trackDao().search("trai").first().size)
    }

    @Test
    fun playlistReorderRewritesPositions() = runBlocking {
        val playlistId = db.playlistDao().create("Test")
        db.trackDao().upsertAll((1L..3L).map { track(id = it, title = "Track $it") })
        db.playlistDao().addTrack(playlistId, 1)
        db.playlistDao().addTrack(playlistId, 2)
        db.playlistDao().addTrack(playlistId, 3)

        db.playlistDao().reorder(playlistId, from = 0, to = 2)

        assertEquals(
            listOf(2L, 3L, 1L),
            db.playlistDao().entries(playlistId).map { it.trackId },
        )
    }

    @Test
    fun playbackStateRoundTripsQueueJson() = runBlocking {
        val store = PlaybackStateStore(db.playbackStateDao())
        store.save(
            queueIds = listOf(4, 5, 6),
            index = 1,
            positionMs = 1234,
            shuffle = true,
            repeat = RepeatMode.ALL,
        )

        val loaded = store.load()!!

        assertEquals(listOf(4L, 5L, 6L), store.queueIds(loaded))
        assertEquals(1, loaded.index)
        assertEquals(1234L, loaded.positionMs)
        assertTrue(loaded.shuffle)
        assertEquals("ALL", loaded.repeat)
    }
}

private fun track(
    id: Long,
    title: String,
    artist: String = "",
    album: String = "",
    albumArtist: String = "",
): TrackEntity = TrackEntity(
    id = id,
    uri = "content://test/$id.flac",
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = 180_000,
    sampleRate = 44_100,
    bitDepth = 16,
    channels = 2,
    hasMd5 = true,
)
