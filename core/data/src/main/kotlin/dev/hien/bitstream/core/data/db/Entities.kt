package dev.hien.bitstream.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["uri"], unique = true),
        Index("album"),
        Index("artist"),
        Index("album_artist"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    @ColumnInfo(name = "album_artist") val albumArtist: String = "",
    @ColumnInfo(name = "track_no") val trackNumber: Int = 0,
    @ColumnInfo(name = "disc_no") val discNumber: Int = 0,
    val year: Int? = null,
    val genre: String = "",
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int = 0,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int = 0,
    val channels: Int = 2,
    @ColumnInfo(name = "has_md5") val hasMd5: Boolean = false,
    @ColumnInfo(name = "art_key") val artKey: String? = null,
    val folder: String = "",
    val size: Long = 0,
    val modified: Long = 0,
    @ColumnInfo(name = "added_at") val addedAt: Long = 0,
)

/**
 * Full-text index over the fields search should match. It is kept in sync by
 * [TrackDao.upsertAll] and [TrackDao.deleteByIds] rather than by triggers, so a
 * bulk scan updates the index exactly once per track.
 */
@Fts4
@Entity(tableName = "track_fts")
data class TrackFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "album_artist") val albumArtist: String,
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "album_artist") val albumArtist: String,
    val year: Int? = null,
    @ColumnInfo(name = "art_key") val artKey: String? = null,
    @ColumnInfo(name = "track_count") val trackCount: Int = 0,
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "album_count") val albumCount: Int = 0,
    @ColumnInfo(name = "track_count") val trackCount: Int = 0,
    @ColumnInfo(name = "art_key") val artKey: String? = null,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlist_id"), Index("track_id")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "track_id") val trackId: Long,
    val position: Int,
)

@Entity(tableName = "scan_folders")
data class ScanFolderEntity(
    @PrimaryKey
    @ColumnInfo(name = "tree_uri")
    val treeUri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "last_scan") val lastScan: Long = 0,
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "queue_json") val queueJson: String,
    val index: Int = 0,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: String = "OFF",
)
