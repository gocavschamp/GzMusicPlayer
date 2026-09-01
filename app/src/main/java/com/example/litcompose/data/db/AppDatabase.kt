package com.example.litcompose.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteTrackEntity::class,
        TrackEntity::class,
        CollectionEntity::class,
        CollectionTrackCrossRef::class,
        LyricsCacheEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun trackDao(): TrackDao
    abstract fun collectionDao(): CollectionDao
    abstract fun lyricsCacheDao(): LyricsCacheDao

    companion object {
        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tracks ADD COLUMN remote TEXT")
                }
            }

        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS lyrics_cache (
                            trackId TEXT NOT NULL PRIMARY KEY,
                            linesJson TEXT,
                            artworkPath TEXT,
                            updatedAtMs INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE collection_tracks ADD COLUMN position INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }
    }
}
