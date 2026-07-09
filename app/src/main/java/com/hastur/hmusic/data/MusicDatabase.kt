package com.hastur.hmusic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        RemoteSongEntity::class,
        BackupProfileEntity::class,
        MusicdlDownloadEntity::class,
        MusicdlPendingDownloadEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_player_database"
                )
                .addMigrations(MIGRATION_7_8)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `musicdl_pending_downloads` (
                        `stableKey` TEXT NOT NULL,
                        `taskId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `itemId` TEXT NOT NULL,
                        `remoteName` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`stableKey`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
