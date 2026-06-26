package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY syncTime DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE isLocal = :isLocal")
    suspend fun clearSongsByType(isLocal: Boolean)

    @Query("DELETE FROM songs")
    suspend fun clearAllSongs()

    @Query("SELECT * FROM oss_config WHERE id = 1 LIMIT 1")
    suspend fun getOssConfig(): OssConfigEntity?

    @Query("SELECT * FROM oss_config WHERE id = 1 LIMIT 1")
    fun getOssConfigFlow(): Flow<OssConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOssConfig(config: OssConfigEntity)
}
