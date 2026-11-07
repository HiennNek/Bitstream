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
        FavoriteEntity::class,
        PlayHistoryEntity::class,
        PlaybackStateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class BitstreamDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun scanFolderDao(): ScanFolderDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun playbackStateDao(): PlaybackStateDao

    companion object {
        private const val NAME = "bitstream.db"

        @Volatile
        private var instance: BitstreamDatabase? = null

        fun create(context: Context): BitstreamDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BitstreamDatabase::class.java,
                    NAME,
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
