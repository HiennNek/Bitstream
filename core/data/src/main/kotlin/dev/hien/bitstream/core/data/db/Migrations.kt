package dev.hien.bitstream.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the favorites table. The SQL matches the exported schema exactly so a
 * fresh install (created from the entity) and an upgrade produce the same table.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorites` (" +
                "`track_id` INTEGER NOT NULL, " +
                "`added_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`track_id`), " +
                "FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
