package dev.hien.bitstream.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        TrackFtsEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        ScanFolderEntity::class,
        PlaybackStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BitstreamDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun scanFolderDao(): ScanFolderDao
    abstract fun playbackStateDao(): PlaybackStateDao

    companion object {
        private const val NAME = "bitstream.db"

        fun create(context: Context): BitstreamDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                BitstreamDatabase::class.java,
                NAME,
            ).build()
    }
}
