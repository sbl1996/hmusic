package com.hastur.hmusic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY syncTime DESC")
    fun getLocalSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE md5sum = :md5sum LIMIT 1")
    suspend fun findLocalSongByMd5(md5sum: String): SongEntity?

    @Query("SELECT * FROM remote_songs ORDER BY syncTime DESC")
    fun getRemoteSongs(): Flow<List<RemoteSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalSong(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteSongs(songs: List<RemoteSongEntity>)

    @Delete
    suspend fun deleteLocalSong(song: SongEntity)

    @Query("DELETE FROM songs")
    suspend fun clearLocalSongs()

    @Query("DELETE FROM remote_songs")
    suspend fun clearRemoteSongs()

    @Query("SELECT * FROM oss_config WHERE id = 1 LIMIT 1")
    suspend fun getOssConfig(): OssConfigEntity?

    @Query("SELECT * FROM oss_config WHERE id = 1 LIMIT 1")
    fun getOssConfigFlow(): Flow<OssConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOssConfig(config: OssConfigEntity)
}
