package com.hastur.hmusic.data

import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {
    val localSongs: Flow<List<SongEntity>> = musicDao.getLocalSongs()
    val backupProfilesFlow: Flow<List<BackupProfileEntity>> = musicDao.getBackupProfilesFlow()
    val activeBackupProfileFlow: Flow<BackupProfileEntity?> = musicDao.getActiveBackupProfileFlow()

    fun getRemoteSongsFlow(profileId: Long): Flow<List<RemoteSongEntity>> {
        return musicDao.getRemoteSongs(profileId)
    }

    suspend fun findLocalSongByMd5(md5sum: String): SongEntity? {
        return musicDao.findLocalSongByMd5(md5sum)
    }

    suspend fun insertLocalSong(song: SongEntity): Long {
        return musicDao.insertLocalSong(song)
    }

    suspend fun insertRemoteSongs(songs: List<RemoteSongEntity>) {
        musicDao.insertRemoteSongs(songs)
    }

    suspend fun deleteLocalSong(song: SongEntity) {
        musicDao.deleteLocalSong(song)
    }

    suspend fun clearLocalSongs() {
        musicDao.clearLocalSongs()
    }

    suspend fun clearRemoteSongs() {
        musicDao.clearRemoteSongs()
    }

    suspend fun clearRemoteSongsByProfileId(profileId: Long) {
        musicDao.clearRemoteSongsByProfileId(profileId)
    }

    suspend fun getActiveBackupProfile(): BackupProfileEntity? {
        return musicDao.getActiveBackupProfile()
    }

    suspend fun getBackupProfile(profileId: Long): BackupProfileEntity? {
        return musicDao.getBackupProfile(profileId)
    }

    suspend fun countBackupProfiles(): Int {
        return musicDao.countBackupProfiles()
    }

    suspend fun insertBackupProfile(profile: BackupProfileEntity): Long {
        return musicDao.insertBackupProfile(profile)
    }

    suspend fun updateBackupProfile(profile: BackupProfileEntity) {
        musicDao.updateBackupProfile(profile)
    }

    suspend fun activateBackupProfile(profileId: Long) {
        musicDao.activateBackupProfile(profileId)
    }

    suspend fun deleteBackupProfile(profileId: Long) {
        musicDao.deleteBackupProfile(profileId)
    }

    suspend fun replaceRemoteSongs(profileId: Long, songs: List<RemoteSongEntity>) {
        musicDao.replaceRemoteSongs(profileId, songs)
    }
}
