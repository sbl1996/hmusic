package com.hastur.hmusic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY syncTime DESC")
    fun getLocalSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE md5sum = :md5sum LIMIT 1")
    suspend fun findLocalSongByMd5(md5sum: String): SongEntity?

    @Query("SELECT * FROM remote_songs WHERE profileId = :profileId ORDER BY syncTime DESC")
    fun getRemoteSongs(profileId: Long): Flow<List<RemoteSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalSong(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteSongs(songs: List<RemoteSongEntity>)

    @Query("UPDATE songs SET durationMs = :durationMs WHERE md5sum = :md5sum AND durationMs != :durationMs")
    suspend fun updateLocalSongDuration(md5sum: String, durationMs: Long)

    @Query("UPDATE remote_songs SET durationMs = :durationMs WHERE md5sum = :md5sum AND durationMs != :durationMs")
    suspend fun updateRemoteSongDuration(md5sum: String, durationMs: Long)

    @Delete
    suspend fun deleteLocalSong(song: SongEntity)

    @Query("DELETE FROM musicdl_downloads WHERE md5sum = :md5sum")
    suspend fun deleteMusicdlDownloadsByMd5(md5sum: String)

    @Query("DELETE FROM songs")
    suspend fun clearLocalSongs()

    @Query("DELETE FROM musicdl_downloads")
    suspend fun clearMusicdlDownloads()

    @Query("DELETE FROM remote_songs")
    suspend fun clearRemoteSongs()

    @Query("DELETE FROM remote_songs WHERE profileId = :profileId")
    suspend fun clearRemoteSongsByProfileId(profileId: Long)

    @Query("SELECT * FROM backup_profiles ORDER BY isActive DESC, updatedAt DESC, id DESC")
    fun getBackupProfilesFlow(): Flow<List<BackupProfileEntity>>

    @Query("SELECT * FROM backup_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveBackupProfile(): BackupProfileEntity?

    @Query("SELECT * FROM backup_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveBackupProfileFlow(): Flow<BackupProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupProfile(profile: BackupProfileEntity): Long

    @Update
    suspend fun updateBackupProfile(profile: BackupProfileEntity)

    @Query("DELETE FROM backup_profiles WHERE id = :profileId")
    suspend fun deleteBackupProfile(profileId: Long)

    @Query("UPDATE backup_profiles SET isActive = 0")
    suspend fun clearActiveBackupProfiles()

    @Query("UPDATE backup_profiles SET isActive = 1, updatedAt = :updatedAt WHERE id = :profileId")
    suspend fun setActiveBackupProfile(profileId: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM backup_profiles")
    suspend fun countBackupProfiles(): Int

    @Query("SELECT * FROM backup_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getBackupProfile(profileId: Long): BackupProfileEntity?

    @Query("SELECT * FROM musicdl_downloads WHERE stableKey IN (:stableKeys)")
    suspend fun getMusicdlDownloadsByStableKeys(stableKeys: List<String>): List<MusicdlDownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicdlDownload(download: MusicdlDownloadEntity): Long

    @Query("DELETE FROM musicdl_downloads WHERE stableKey IN (:stableKeys)")
    suspend fun deleteMusicdlDownloadsByStableKeys(stableKeys: List<String>)

    @Query("SELECT * FROM musicdl_pending_downloads WHERE stableKey = :stableKey LIMIT 1")
    suspend fun getMusicdlPendingDownload(stableKey: String): MusicdlPendingDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMusicdlPendingDownload(download: MusicdlPendingDownloadEntity)

    @Query("DELETE FROM musicdl_pending_downloads WHERE stableKey = :stableKey")
    suspend fun deleteMusicdlPendingDownload(stableKey: String)

    @Query("DELETE FROM musicdl_pending_downloads")
    suspend fun clearMusicdlPendingDownloads()

    @Transaction
    suspend fun activateBackupProfile(profileId: Long, updatedAt: Long = System.currentTimeMillis()) {
        clearActiveBackupProfiles()
        setActiveBackupProfile(profileId, updatedAt)
    }

    @Transaction
    suspend fun replaceRemoteSongs(profileId: Long, songs: List<RemoteSongEntity>) {
        clearRemoteSongsByProfileId(profileId)
        if (songs.isNotEmpty()) {
            insertRemoteSongs(songs)
        }
    }
}
