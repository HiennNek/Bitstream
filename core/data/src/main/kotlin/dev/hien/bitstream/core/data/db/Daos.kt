package dev.hien.bitstream.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(rows: List<TrackFtsEntity>)

    @Query("DELETE FROM track_fts WHERE rowid IN (:ids)")
    suspend fun deleteFts(ids: List<Long>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracks(ids: List<Long>)

    @Transaction
    suspend fun upsertAll(tracks: List<TrackEntity>) {
        insertAll(tracks)
        deleteFts(tracks.map { it.id })
        insertFts(tracks.map { it.toFts() })
    }

    @Transaction
    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        deleteFts(ids)
        deleteTracks(ids)
    }

    @Query(
        """
        SELECT * FROM tracks
        ORDER BY album_artist COLLATE NOCASE, album COLLATE NOCASE,
                 disc_no, track_no, title COLLATE NOCASE
        """,
    )
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT tracks.* FROM tracks
        INNER JOIN track_fts ON tracks.id = track_fts.rowid
        WHERE track_fts MATCH :query
        ORDER BY tracks.title COLLATE NOCASE
        """,
    )
    fun searchRaw(query: String): Flow<List<TrackEntity>>

    /**
     * Prefix search, so "colt" finds "Coltrane". Blank or punctuation-only
     * input returns an empty result instead of an FTS syntax error.
     */
    fun search(query: String): Flow<List<TrackEntity>> {
        val ftsQuery = query.toFtsPrefixQuery()
        return if (ftsQuery.isEmpty()) flowOf(emptyList()) else searchRaw(ftsQuery)
    }

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun tracksByIds(ids: List<Long>): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int
}

@Dao
interface AlbumDao {
    @Query("DELETE FROM albums")
    suspend fun clear()

    @Query(
        """
        INSERT INTO albums (id, title, album_artist, year, art_key, track_count)
        SELECT
            COALESCE(NULLIF(album_artist, ''), artist) || '|' || album AS id,
            album AS title,
            COALESCE(NULLIF(album_artist, ''), artist) AS album_artist,
            MIN(year) AS year,
            MIN(art_key) AS art_key,
            COUNT(*) AS track_count
        FROM tracks
        WHERE album != ''
        GROUP BY COALESCE(NULLIF(album_artist, ''), artist), album
        """,
    )
    suspend fun insertDerived()

    @Transaction
    suspend fun rebuild() {
        clear()
        insertDerived()
    }

    @Query(
        """
        SELECT * FROM albums
        ORDER BY album_artist COLLATE NOCASE, year, title COLLATE NOCASE
        """,
    )
    fun observeAlbums(): Flow<List<AlbumEntity>>
}

@Dao
interface ArtistDao {
    @Query("DELETE FROM artists")
    suspend fun clear()

    @Query(
        """
        INSERT INTO artists (id, name, album_count, track_count, art_key)
        SELECT name, name, COUNT(DISTINCT album), COUNT(*), MIN(art_key)
        FROM (
            SELECT artist AS name, album, art_key FROM tracks WHERE artist != ''
            UNION ALL
            SELECT album_artist AS name, album, art_key
            FROM tracks
            WHERE album_artist != '' AND album_artist != artist
        )
        GROUP BY name
        """,
    )
    suspend fun insertDerived()

    @Transaction
    suspend fun rebuild() {
        clear()
        insertDerived()
    }

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    fun observeArtists(): Flow<List<ArtistEntity>>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Transaction
    suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
    }

    @Query("UPDATE playlists SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertEntry(entry: PlaylistEntryEntity): Long

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Transaction
    suspend fun addTrack(playlistId: Long, trackId: Long) {
        insertEntry(
            PlaylistEntryEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = nextPosition(playlistId),
            ),
        )
    }

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun entries(playlistId: Long): List<PlaylistEntryEntity>

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntryEntity>>

    @Query("UPDATE playlist_entries SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Transaction
    suspend fun reorder(playlistId: Long, from: Int, to: Int) {
        val list = entries(playlistId).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val moved = list.removeAt(from)
        list.add(to, moved)
        list.forEachIndexed { index, entry -> updatePosition(entry.id, index) }
    }

    @Query("DELETE FROM playlist_entries WHERE id = :entryId")
    suspend fun removeEntry(entryId: Long)
}

@Dao
interface ScanFolderDao {
    @Query("SELECT * FROM scan_folders ORDER BY display_name COLLATE NOCASE")
    fun observeFolders(): Flow<List<ScanFolderEntity>>

    @Upsert
    suspend fun upsert(folder: ScanFolderEntity)

    @Query("DELETE FROM scan_folders WHERE tree_uri = :treeUri")
    suspend fun delete(treeUri: String)
}

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun load(): PlaybackStateEntity?

    @Upsert
    suspend fun save(state: PlaybackStateEntity)
}

private fun TrackEntity.toFts(): TrackFtsEntity = TrackFtsEntity(
    trackId = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
)

private fun String.toFtsPrefixQuery(): String =
    trim()
        .split(Regex("\\s+"))
        .map { token -> token.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { "$it*" }
