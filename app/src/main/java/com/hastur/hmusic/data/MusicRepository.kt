package com.hastur.hmusic.data

import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {
    val localSongs: Flow<List<SongEntity>> = musicDao.getLocalSongs()
    val remoteSongs: Flow<List<RemoteSongEntity>> = musicDao.getRemoteSongs()
    val ossConfigFlow: Flow<OssConfigEntity?> = musicDao.getOssConfigFlow()

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

    suspend fun getOssConfig(): OssConfigEntity? {
        return musicDao.getOssConfig()
    }

    suspend fun insertOssConfig(config: OssConfigEntity) {
        musicDao.insertOssConfig(config)
    }
}
