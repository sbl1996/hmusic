package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.MusicRepository
import com.example.data.OssConfigEntity
import com.example.data.SongEntity
import com.example.player.LoopMode
import com.example.player.MusicPlayerManager
import com.example.sync.OssClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Completed(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

class MusicViewModel(
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    @Suppress("UNCHECKED_CAST")
    private val backupSongListAdapter = moshi.adapter(
        Types.newParameterizedType(List::class.java, BackupSong::class.java)
    ) as JsonAdapter<List<BackupSong>>

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Database Songs Flow
    val allSongs: StateFlow<List<SongEntity>> = repository.allSongs
        .onEach { songs ->
            // Update current playing catalog within player playerManager
            playerManager.setPlaylist(songs)
            
            // Seed default tracks if completely empty
            if (songs.isEmpty()) {
                seedDefaultSongs()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val ossConfig: StateFlow<OssConfigEntity?> = repository.ossConfigFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Player State flows delegated from PlayerManager
    val currentSong: StateFlow<SongEntity?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val loopMode: StateFlow<LoopMode> = playerManager.loopMode
    val playerError: StateFlow<String?> = playerManager.errorMessage

    private var ossClient: OssClient? = null

    init {
        // Automatically check if there is an existing database configuration.
        // If not, and there are values configured in BuildConfig, populate them as default.
        viewModelScope.launch {
            try {
                val existing = repository.getOssConfig()
                if (existing == null) {
                    val envEndpoint = try { com.example.BuildConfig.S3_ENDPOINT } catch (e: Exception) { "" }
                    val envRegion = try { com.example.BuildConfig.S3_REGION } catch (e: Exception) { "" }
                    val envForcePathStyle = try { com.example.BuildConfig.S3_FORCE_PATH_STYLE } catch (e: Exception) { "" }
                    val envBucket = try { com.example.BuildConfig.S3_BUCKET } catch (e: Exception) { "" }
                    val envAccessKey = try { com.example.BuildConfig.S3_ACCESS_KEY_ID } catch (e: Exception) { "" }
                    val envSecretKey = try { com.example.BuildConfig.S3_SECRET_ACCESS_KEY } catch (e: Exception) { "" }
                    val envPrefix = try { com.example.BuildConfig.S3_PREFIX } catch (e: Exception) { "" }

                    val isEndpointValid = envEndpoint.isNotEmpty() && envEndpoint != "YOUR_S3_ENDPOINT"
                    val isAccessKeyValid = envAccessKey.isNotEmpty() && envAccessKey != "YOUR_S3_ACCESS_KEY_ID"
                    val isSecretValid = envSecretKey.isNotEmpty() && envSecretKey != "YOUR_S3_SECRET_ACCESS_KEY"

                    if (isEndpointValid || isAccessKeyValid || isSecretValid) {
                        val defaultBucket = if (envBucket == "YOUR_S3_BUCKET") "" else envBucket
                        val defaultPrefix = if (envPrefix == "YOUR_S3_PREFIX") "" else envPrefix
                        val normalizedEndpoint = if (isEndpointValid) envEndpoint else ""
                        val defaultRegion = when {
                            envRegion.isBlank() || envRegion == "YOUR_S3_REGION" ->
                                OssClient.defaultRegion(normalizedEndpoint)
                            else -> envRegion
                        }
                        val defaultForcePathStyle = when (envForcePathStyle.lowercase()) {
                            "true", "1", "yes", "y" -> true
                            "false", "0", "no", "n" -> false
                            else -> OssClient.defaultForcePathStyle(normalizedEndpoint)
                        }

                        val initialConfig = OssConfigEntity(
                            id = 1,
                            endpoint = normalizedEndpoint,
                            region = defaultRegion,
                            forcePathStyle = defaultForcePathStyle,
                            accessKeyId = if (isAccessKeyValid) envAccessKey else "",
                            accessKeySecret = if (isSecretValid) envSecretKey else "",
                            bucket = defaultBucket,
                            prefix = defaultPrefix
                        )
                        repository.insertOssConfig(initialConfig)
                        _syncState.value = SyncState.Completed("已自动从 .env 生效配置")
                    }
                }
            } catch (e: Exception) {
                // Ignore config bootstrapping errors
            }
        }

        // Automatically monitor S3/OSS Config and update client
        viewModelScope.launch {
            repository.ossConfigFlow.collect { config ->
                ossClient?.close()
                if (config != null && config.endpoint.isNotEmpty()) {
                    ossClient = OssClient(
                        endpoint = config.endpoint,
                        region = config.region.ifBlank { OssClient.defaultRegion(config.endpoint) },
                        forcePathStyle = config.forcePathStyle,
                        bucket = config.bucket,
                        accessKeyId = config.accessKeyId,
                        accessKeySecret = config.accessKeySecret,
                        prefix = config.prefix
                    )
                } else {
                    ossClient = null
                }
            }
        }
    }

    private fun seedDefaultSongs() {
        viewModelScope.launch {
            val defaults = listOf(
                SongEntity(
                    title = "Aurora Ambient",
                    artist = "SoundHelix Orchestra",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    isLocal = true,
                    fileName = "SoundHelix-Song-1.mp3"
                ),
                SongEntity(
                    title = "Borealis Electronic Sync",
                    artist = "Helix Project",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    isLocal = true,
                    fileName = "SoundHelix-Song-2.mp3"
                ),
                SongEntity(
                    title = "Guitar Sunset Dream",
                    artist = "Acoustic Horizon",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                    isLocal = true,
                    fileName = "SoundHelix-Song-4.mp3"
                )
            )
            repository.insertSongs(defaults)
        }
    }

    fun playSong(song: SongEntity) {
        playerManager.playSong(song)
    }

    fun playOrPause() {
        playerManager.playOrPause()
    }

    fun playNext() {
        playerManager.playNext()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun toggleLoopMode() {
        playerManager.toggleLoopMode()
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    // Aliyun OSS Operations
    fun saveOssConfig(
        endpoint: String,
        region: String,
        forcePathStyle: Boolean,
        bucket: String,
        accessKeyId: String,
        accessKeySecret: String,
        prefix: String
    ) {
        viewModelScope.launch {
            val normalizedEndpoint = endpoint.trim()
            val config = OssConfigEntity(
                endpoint = normalizedEndpoint,
                region = region.trim().ifEmpty { OssClient.defaultRegion(normalizedEndpoint) },
                forcePathStyle = forcePathStyle,
                bucket = bucket.trim(),
                accessKeyId = accessKeyId.trim(),
                accessKeySecret = accessKeySecret.trim(),
                prefix = prefix.trim()
            )
            repository.insertOssConfig(config)
            // Show auto success sync
            _syncState.value = SyncState.Completed("配置已保存")
            delay(500)
            syncFromOSS()
        }
    }

    fun syncFromOSS() {
        val client = ossClient
        if (client == null) {
            _syncState.value = SyncState.Error("请先在设置中配置有效的 S3 / OSS 账号信息")
            return
        }
        _syncState.value = SyncState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = client.listMusicFiles()
                if (list.isEmpty()) {
                    _syncState.value = SyncState.Completed("云同步完成：在您的 S3 / OSS 存储桶目录中未找到支持的音频文件(MP3/WAV等)。")
                    return@launch
                }

                // Map list keys to track entities
                val remoteSongs = list.map { key ->
                    val fileName = key.substringAfterLast("/")
                    val title = fileName.substringBeforeLast(".").ifEmpty { fileName }
                    SongEntity(
                        title = title,
                        artist = "S3 云音源",
                        url = client.makeFileUrl(key),
                        isLocal = false,
                        fileName = fileName
                    )
                }

                // Clear previous downloaded tracks to avoid duplicate objects, insert fresh scanned files
                repository.clearSongsByType(isLocal = false)
                repository.insertSongs(remoteSongs)

                _syncState.value = SyncState.Completed("云同步成功：共发现了 ${remoteSongs.size} 首云端歌曲！")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("连接或拉取 S3 / OSS 失败: ${e.localizedMessage}")
            }
        }
    }

    fun backupPlaylistToOSS() {
        val client = ossClient
        if (client == null) {
            _syncState.value = SyncState.Error("请先配置 S3 / OSS 后再上传备份")
            return
        }
        _syncState.value = SyncState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songs = allSongs.value
                val playlistJson = backupSongListAdapter.toJson(songs.map(BackupSong::fromSongEntity))

                val success = client.uploadPlaylist(playlistJson)
                if (success) {
                    _syncState.value = SyncState.Completed("播放列表已成功云备份至 `music_playlist_sync.json`！")
                } else {
                    _syncState.value = SyncState.Error("备份文件写入失败：请检查云端 S3 / OSS 的存储桶 Bucket 访问策略或签名权限。")
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("上传备份程序异常: ${e.localizedMessage}")
            }
        }
    }

    fun restorePlaylistFromCloud() {
        val client = ossClient
        if (client == null) {
            _syncState.value = SyncState.Error("请先配置 S3 / OSS 信息以进行还原")
            return
        }
        _syncState.value = SyncState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = client.downloadPlaylist()
                if (json == null) {
                    _syncState.value = SyncState.Error("云端未找到任何已经备份的 `music_playlist_sync.json` 文件")
                    return@launch
                }

                val restoredSongs = backupSongListAdapter.fromJson(json)
                    ?.mapNotNull { it.toSongEntityOrNull() }
                    .orEmpty()

                if (restoredSongs.isEmpty()) {
                    _syncState.value = SyncState.Completed("还原错误：云端备份文件解析出的音频列表为空。")
                    return@launch
                }

                repository.clearAllSongs()
                repository.insertSongs(restoredSongs)
                _syncState.value = SyncState.Completed("播放列表同步还原成功：已成功恢复 ${restoredSongs.size} 首歌曲！")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("从云端还原列表失败: ${e.localizedMessage}")
            }
        }
    }

    fun addLocalSong(title: String, artist: String, url: String) {
        viewModelScope.launch {
            val song = SongEntity(
                title = title.trim().ifEmpty { "极简本地歌曲" },
                artist = artist.trim().ifEmpty { "本地乐迷" },
                url = url,
                isLocal = true,
                fileName = url.substringAfterLast("/")
            )
            repository.insertSong(song)
        }
    }

    fun deleteSong(song: SongEntity) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }

    fun clearPlaylist() {
        viewModelScope.launch {
            repository.clearAllSongs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ossClient?.close()
        playerManager.clear()
    }
}

@JsonClass(generateAdapter = true)
data class BackupSong(
    val title: String,
    val artist: String,
    val url: String,
    val durationMs: Long = 0,
    val isLocal: Boolean = true,
    val fileName: String = "",
    val syncTime: Long = 0
) {
    fun toSongEntityOrNull(): SongEntity? {
        if (title.isBlank() || url.isBlank()) return null
        return SongEntity(
            title = title,
            artist = artist.ifBlank { "备份歌手" },
            url = url,
            durationMs = durationMs,
            isLocal = isLocal,
            fileName = fileName,
            syncTime = syncTime
        )
    }

    companion object {
        fun fromSongEntity(song: SongEntity): BackupSong {
            return BackupSong(
                title = song.title,
                artist = song.artist,
                url = song.url,
                durationMs = song.durationMs,
                isLocal = song.isLocal,
                fileName = song.fileName,
                syncTime = song.syncTime
            )
        }
    }
}

class MusicViewModelFactory(
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(repository, playerManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
