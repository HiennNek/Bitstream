package dev.hien.bitstream.core.data

import dev.hien.bitstream.core.data.db.AlbumDao
import dev.hien.bitstream.core.data.db.AlbumEntity
import dev.hien.bitstream.core.data.db.ArtistDao
import dev.hien.bitstream.core.data.db.ArtistEntity
import dev.hien.bitstream.core.data.db.FavoriteDao
import dev.hien.bitstream.core.data.db.PlayHistoryDao
import dev.hien.bitstream.core.data.db.TrackDao
import dev.hien.bitstream.core.data.db.TrackEntity
import dev.hien.bitstream.core.model.Album
import dev.hien.bitstream.core.model.Artist
import dev.hien.bitstream.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The read side the UI needs. Kept as an interface so ViewModels can be tested with fakes. */
interface MusicRepository {
    val tracks: Flow<List<Track>>
    val albums: Flow<List<Album>>
    val artists: Flow<List<Artist>>
    val favoriteTracks: Flow<List<Track>>
    val favoriteIds: Flow<Set<Long>>

    /** Most recently played tracks, newest first, including the current one. */
    val recentTracks: Flow<List<Track>>

    fun search(query: String): Flow<List<Track>>

    suspend fun tracksByIds(ids: List<Long>): List<Track>

    suspend fun setFavorite(trackId: Long, favorite: Boolean)

    suspend fun recordPlay(trackId: Long)

    /** Rebuild the derived album and artist tables after a scan or an incremental update. */
    suspend fun rebuildDerived()
}

class RoomMusicRepository(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val favoriteDao: FavoriteDao,
    private val playHistoryDao: PlayHistoryDao,
) : MusicRepository {
    // Room emits on its query executor but the entity -> model mapping runs in
    // the collector's context, which is the main thread for the library
    // ViewModels. Mapping every track there janked the library scene.
    override val tracks: Flow<List<Track>> =
        trackDao.observeTracks().map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.Default)

    override val albums: Flow<List<Album>> =
        albumDao.observeAlbums().map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.Default)

    override val artists: Flow<List<Artist>> =
        artistDao.observeArtists().map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.Default)

    override fun search(query: String): Flow<List<Track>> =
        trackDao.search(query).map { entities -> entities.map { it.toModel() } }

    override val favoriteTracks: Flow<List<Track>> =
        favoriteDao.observeTracks().map { entities -> entities.map { it.toModel() } }

    override val favoriteIds: Flow<Set<Long>> =
        favoriteDao.observeIds().map { it.toSet() }

    override val recentTracks: Flow<List<Track>> =
        playHistoryDao.observeTracks(MAX_RECENT_TRACKS).map { entities -> entities.map { it.toModel() } }

    override suspend fun tracksByIds(ids: List<Long>): List<Track> =
        trackDao.tracksByIds(ids).map { it.toModel() }

    override suspend fun setFavorite(trackId: Long, favorite: Boolean) {
        favoriteDao.set(trackId, favorite)
    }

    override suspend fun recordPlay(trackId: Long) {
        playHistoryDao.record(trackId, System.currentTimeMillis())
    }

    override suspend fun rebuildDerived() {
        albumDao.rebuild()
        artistDao.rebuild()
    }
}

fun TrackEntity.toModel(): Track = Track(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    trackNumber = trackNumber,
    discNumber = discNumber,
    durationMs = durationMs,
    sampleRate = sampleRate,
    bitDepth = bitDepth,
    channels = channels,
    hasMd5 = hasMd5,
    artworkUri = artKey,
    year = year,
    genre = genre,
    folder = folder,
    size = size,
    modified = modified,
)

fun Track.toEntity(addedAt: Long = System.currentTimeMillis()): TrackEntity = TrackEntity(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    genre = genre,
    durationMs = durationMs,
    sampleRate = sampleRate,
    bitDepth = bitDepth,
    channels = channels,
    hasMd5 = hasMd5,
    artKey = artworkUri,
    folder = folder,
    size = size,
    modified = modified,
    addedAt = addedAt,
)

fun AlbumEntity.toModel(): Album = Album(
    id = id,
    title = title,
    albumArtist = albumArtist,
    artworkUri = artKey,
    trackCount = trackCount,
    year = year,
)

fun ArtistEntity.toModel(): Artist = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    trackCount = trackCount,
    artworkUri = artKey,
)

private const val MAX_RECENT_TRACKS = 20
