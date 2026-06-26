package com.example.data

import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {
    val allSongs: Flow<List<SongEntity>> = musicDao.getAllSongs()
    val ossConfigFlow: Flow<OssConfigEntity?> = musicDao.getOssConfigFlow()

    suspend fun insertSong(song: SongEntity): Long {
        return musicDao.insertSong(song)
    }

    suspend fun insertSongs(songs: List<SongEntity>) {
        musicDao.insertSongs(songs)
    }

    suspend fun deleteSong(song: SongEntity) {
        musicDao.deleteSong(song)
    }

    suspend fun clearSongsByType(isLocal: Boolean) {
        musicDao.clearSongsByType(isLocal)
    }

    suspend fun clearAllSongs() {
        musicDao.clearAllSongs()
    }

    suspend fun getOssConfig(): OssConfigEntity? {
        return musicDao.getOssConfig()
    }

    suspend fun insertOssConfig(config: OssConfigEntity) {
        musicDao.insertOssConfig(config)
    }
}
