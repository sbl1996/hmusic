package com.hastur.hmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hastur.hmusic.data.MusicRepository
import com.hastur.hmusic.data.OssConfigEntity
import com.hastur.hmusic.data.RemoteSongEntity
import com.hastur.hmusic.data.SongEntity
import com.hastur.hmusic.player.LoopMode
import com.hastur.hmusic.player.MusicPlayerManager
import com.hastur.hmusic.player.PlaybackStateStore
import com.hastur.hmusic.sync.OssClient
import com.hastur.hmusic.sync.SongStorage
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class LibrarySongStatus {
    LOCAL_ONLY,
    REMOTE_ONLY,
    BACKED_UP
}

data class LibrarySongItem(
    val md5sum: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    val fileName: String = "",
    val mimeType: String = "",
    val status: LibrarySongStatus,
    val localSong: SongEntity? = null,
    val remoteSong: RemoteSongEntity? = null
) {
    val isDownloaded: Boolean
        get() = localSong?.isDownloaded == true

    val canDownload: Boolean
        get() = remoteSong != null && !isDownloaded
}

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Completed(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

class MusicViewModel(
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val songStorage: SongStorage,
    private val playbackStateStore: PlaybackStateStore
) : ViewModel() {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val manifestAdapter: JsonAdapter<CloudPlaylistManifest> =
        moshi.adapter(CloudPlaylistManifest::class.java)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    val localSongs: StateFlow<List<SongEntity>> = repository.localSongs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val remoteSongs: StateFlow<List<RemoteSongEntity>> = repository.remoteSongs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSongs: StateFlow<List<LibrarySongItem>> = combine(localSongs, remoteSongs) { locals, remotes ->
        playerManager.setPlaylist(locals.filter { it.isDownloaded })
        buildLibraryItems(locals, remotes)
    }.stateIn(
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

    val currentSong: StateFlow<SongEntity?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val loopMode: StateFlow<LoopMode> = playerManager.loopMode
    val playerError: StateFlow<String?> = playerManager.errorMessage

    private var ossClient: OssClient? = null

    init {
        viewModelScope.launch {
            tryBootstrapOssConfig()
        }

        viewModelScope.launch(Dispatchers.IO) {
            restorePlaybackState()
        }

        viewModelScope.launch {
            repository.ossConfigFlow.collect { config ->
                ossClient?.close()
                ossClient = if (config != null && config.endpoint.isNotEmpty()) {
                    OssClient(
                        endpoint = config.endpoint,
                        region = config.region.ifBlank { OssClient.defaultRegion(config.endpoint) },
                        forcePathStyle = config.forcePathStyle,
                        bucket = config.bucket,
                        accessKeyId = config.accessKeyId,
                        accessKeySecret = config.accessKeySecret,
                        prefix = config.prefix
                    )
                } else {
                    null
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                persistPlaybackStateInternal()
            }
        }
    }

    fun playSong(song: LibrarySongItem) {
        val localSong = song.localSong
        if (localSong == null || !localSong.isDownloaded) {
            _syncState.value = SyncState.Error("当前歌曲未下载到本地")
            return
        }
        playerManager.playSong(localSong)
        persistPlaybackState()
    }

    fun downloadSong(song: LibrarySongItem) {
        val client = ossClient
        val remoteSong = song.remoteSong
        if (client == null || remoteSong == null) {
            _syncState.value = SyncState.Error("当前歌曲没有可用的云端备份")
            return
        }
        _syncState.value = SyncState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stored = client.downloadSongStream(remoteSong.remoteKey).use {
                    songStorage.storeRemoteStream(remoteSong.remoteKey, it)
                }
                if (stored.md5sum != remoteSong.md5sum) {
                    stored.file.delete()
                    error("下载后的文件校验失败")
                }

                val local = SongEntity(
                    id = song.localSong?.id ?: 0,
                    md5sum = remoteSong.md5sum,
                    title = song.localSong?.title ?: remoteSong.title,
                    artist = song.localSong?.artist ?: remoteSong.artist,
                    durationMs = if (remoteSong.durationMs > 0) remoteSong.durationMs else song.localSong?.durationMs ?: 0,
                    fileName = remoteSong.fileName.ifBlank { stored.fileName },
                    mimeType = remoteSong.mimeType.ifBlank { stored.mimeType },
                    localPath = stored.file.absolutePath,
                    localUpdatedAt = stored.updatedAt,
                    syncTime = System.currentTimeMillis()
                )
                repository.insertLocalSong(local)
                _syncState.value = SyncState.Completed("已同步到本地：${local.title}")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("下载歌曲失败: ${e.localizedMessage}")
            }
        }
    }

    fun playOrPause() {
        playerManager.playOrPause()
        persistPlaybackState()
    }

    fun playNext() {
        playerManager.playNext()
        persistPlaybackState()
    }

    fun playPrevious() {
        playerManager.playPrevious()
        persistPlaybackState()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        persistPlaybackState()
    }

    fun toggleLoopMode() {
        playerManager.toggleLoopMode()
        persistPlaybackState()
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

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
            repository.insertOssConfig(
                OssConfigEntity(
                    endpoint = normalizedEndpoint,
                    region = region.trim().ifEmpty { OssClient.defaultRegion(normalizedEndpoint) },
                    forcePathStyle = forcePathStyle,
                    bucket = bucket.trim(),
                    accessKeyId = accessKeyId.trim(),
                    accessKeySecret = accessKeySecret.trim(),
                    prefix = prefix.trim()
                )
            )
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
                val manifest = loadManifest(client)
                val remoteEntities = manifest?.songs?.map { it.toRemoteSongEntity() }.orEmpty()
                repository.clearRemoteSongs()
                if (remoteEntities.isNotEmpty()) {
                    repository.insertRemoteSongs(remoteEntities)
                }

                val downloadedCount = localSongs.value.count { it.isDownloaded }
                _syncState.value = SyncState.Completed(
                    "歌单索引已同步：云端 ${remoteEntities.size} 首，本地 ${downloadedCount} 首"
                )
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("同步歌单失败: ${e.localizedMessage}")
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
                val now = System.currentTimeMillis()
                val syncedRemoteSongs = localSongs.value
                    .mapNotNull { normalizeLocalSong(it) }
                    .map { localSong ->
                        val remoteKey = client.buildRemoteAudioKey(localSong.md5sum, localSong.fileName.ifBlank { File(localSong.localPath).name })
                        client.uploadSongFile(remoteKey, File(localSong.localPath), localSong.mimeType)
                        RemoteSongEntity(
                            md5sum = localSong.md5sum,
                            title = localSong.title,
                            artist = localSong.artist,
                            durationMs = localSong.durationMs,
                            fileName = localSong.fileName,
                            mimeType = localSong.mimeType,
                            remoteKey = remoteKey,
                            manifestUpdatedAt = maxOf(localSong.localUpdatedAt, now),
                            syncTime = now
                        )
                    }

                val manifestJson = manifestAdapter.toJson(
                    CloudPlaylistManifest(
                        version = 1,
                        updatedAt = now,
                        songs = syncedRemoteSongs.map { CloudSong.fromRemoteSong(it) }
                    )
                )
                val success = client.uploadManifest(manifestJson)
                if (!success) {
                    error("云端清单写入失败")
                }

                repository.clearRemoteSongs()
                if (syncedRemoteSongs.isNotEmpty()) {
                    repository.insertRemoteSongs(syncedRemoteSongs)
                }
                _syncState.value = SyncState.Completed("本地歌曲已备份到云端，歌单索引已刷新")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("上传备份失败: ${e.localizedMessage}")
            }
        }
    }

    fun addLocalSong(title: String, artist: String, uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stored = songStorage.importFromUri(uri)
                val existing = repository.findLocalSongByMd5(stored.md5sum)
                val song = SongEntity(
                    id = existing?.id ?: 0,
                    md5sum = stored.md5sum,
                    title = title.trim().ifBlank { existing?.title ?: stored.fileName.substringBeforeLast(".") },
                    artist = artist.trim().ifBlank { existing?.artist ?: "本地乐迷" },
                    durationMs = existing?.durationMs ?: 0,
                    fileName = stored.fileName,
                    mimeType = stored.mimeType,
                    localPath = stored.file.absolutePath,
                    localUpdatedAt = stored.updatedAt,
                    syncTime = System.currentTimeMillis()
                )
                repository.insertLocalSong(song)
                _syncState.value = SyncState.Completed("已导入本地歌曲：${song.title}")
                viewModelScope.launch {
                    playerManager.playSong(song)
                    persistPlaybackState()
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("导入本地歌曲失败: ${e.localizedMessage}")
            }
        }
    }

    fun deleteSong(song: LibrarySongItem) {
        val localSong = song.localSong ?: return
        viewModelScope.launch {
            repository.deleteLocalSong(localSong)
        }
    }

    fun clearPlaylist() {
        viewModelScope.launch {
            repository.clearLocalSongs()
            playbackStateStore.clear()
        }
    }

    fun persistPlaybackState() {
        viewModelScope.launch(Dispatchers.IO) {
            persistPlaybackStateInternal()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ossClient?.close()
    }

    private suspend fun tryBootstrapOssConfig() {
        try {
            val existing = repository.getOssConfig()
            if (existing != null) return

            val envEndpoint = try { com.hastur.hmusic.BuildConfig.S3_ENDPOINT } catch (_: Exception) { "" }
            val envRegion = try { com.hastur.hmusic.BuildConfig.S3_REGION } catch (_: Exception) { "" }
            val envForcePathStyle = try { com.hastur.hmusic.BuildConfig.S3_FORCE_PATH_STYLE } catch (_: Exception) { "" }
            val envBucket = try { com.hastur.hmusic.BuildConfig.S3_BUCKET } catch (_: Exception) { "" }
            val envAccessKey = try { com.hastur.hmusic.BuildConfig.S3_ACCESS_KEY_ID } catch (_: Exception) { "" }
            val envSecretKey = try { com.hastur.hmusic.BuildConfig.S3_SECRET_ACCESS_KEY } catch (_: Exception) { "" }
            val envPrefix = try { com.hastur.hmusic.BuildConfig.S3_PREFIX } catch (_: Exception) { "" }

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

                repository.insertOssConfig(
                    OssConfigEntity(
                        id = 1,
                        endpoint = normalizedEndpoint,
                        region = defaultRegion,
                        forcePathStyle = defaultForcePathStyle,
                        accessKeyId = if (isAccessKeyValid) envAccessKey else "",
                        accessKeySecret = if (isSecretValid) envSecretKey else "",
                        bucket = defaultBucket,
                        prefix = defaultPrefix
                    )
                )
                _syncState.value = SyncState.Completed("已自动从 .env 生效配置")
            }
        } catch (_: Exception) {
            // Ignore config bootstrapping errors.
        }
    }

    private fun normalizeLocalSong(song: SongEntity): SongEntity? {
        if (!song.isDownloaded) return null
        return song.copy(syncTime = System.currentTimeMillis())
    }

    private suspend fun loadManifest(client: OssClient): CloudPlaylistManifest? {
        val json = client.downloadManifest() ?: return null
        return manifestAdapter.fromJson(json)
    }

    private suspend fun restorePlaybackState() {
        val savedState = playbackStateStore.read() ?: return
        val localSong = repository.findLocalSongByMd5(savedState.currentSongMd5)
        if (localSong == null || !localSong.isDownloaded) {
            playbackStateStore.clear()
            return
        }
        withContext(Dispatchers.Main) {
            playerManager.setLoopMode(
                when (savedState.loopMode) {
                    LoopMode.SINGLE.name -> LoopMode.SINGLE
                    else -> LoopMode.LIST
                }
            )
            playerManager.restoreSong(localSong, savedState.currentPositionMs)
        }
    }

    private suspend fun persistPlaybackStateInternal() {
        val snapshot = withContext(Dispatchers.Main) {
            playerManager.snapshotPlayback()
        }
        val song = snapshot.song
        if (song == null || !song.isDownloaded) {
            return
        }
        playbackStateStore.save(
            currentSongMd5 = song.md5sum,
            currentPositionMs = snapshot.positionMs,
            isPlaying = snapshot.isPlaying,
            loopMode = loopMode.value.name
        )
    }

    private fun buildLibraryItems(
        locals: List<SongEntity>,
        remotes: List<RemoteSongEntity>
    ): List<LibrarySongItem> {
        val localByMd5 = locals.associateBy { it.md5sum }
        val remoteByMd5 = remotes.associateBy { it.md5sum }
        val orderedKeys = buildList {
            addAll(locals.map { it.md5sum })
            remotes.map { it.md5sum }.forEach { if (!contains(it)) add(it) }
        }

        return orderedKeys.mapNotNull { md5 ->
            val local = localByMd5[md5]
            val remote = remoteByMd5[md5]
            if (local == null && remote == null) return@mapNotNull null

            val status = when {
                local != null && remote != null -> LibrarySongStatus.BACKED_UP
                local != null -> LibrarySongStatus.LOCAL_ONLY
                else -> LibrarySongStatus.REMOTE_ONLY
            }

            LibrarySongItem(
                md5sum = md5,
                title = local?.title ?: remote?.title.orEmpty(),
                artist = local?.artist ?: remote?.artist.orEmpty(),
                durationMs = local?.durationMs ?: remote?.durationMs ?: 0,
                fileName = local?.fileName ?: remote?.fileName.orEmpty(),
                mimeType = local?.mimeType ?: remote?.mimeType.orEmpty(),
                status = status,
                localSong = local,
                remoteSong = remote
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class CloudPlaylistManifest(
    val version: Int = 1,
    val updatedAt: Long = 0,
    val songs: List<CloudSong> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CloudSong(
    val md5sum: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    val fileName: String = "",
    val mimeType: String = "",
    val remoteKey: String = "",
    val updatedAt: Long = 0
) {
    fun toRemoteSongEntity(): RemoteSongEntity {
        return RemoteSongEntity(
            md5sum = md5sum,
            title = title.ifBlank { fileName.substringBeforeLast(".") },
            artist = artist.ifBlank { "云端备份" },
            durationMs = durationMs,
            fileName = fileName,
            mimeType = mimeType,
            remoteKey = remoteKey,
            manifestUpdatedAt = updatedAt,
            syncTime = System.currentTimeMillis()
        )
    }

    companion object {
        fun fromRemoteSong(song: RemoteSongEntity): CloudSong {
            return CloudSong(
                md5sum = song.md5sum,
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                fileName = song.fileName,
                mimeType = song.mimeType,
                remoteKey = song.remoteKey,
                updatedAt = song.manifestUpdatedAt
            )
        }
    }
}

class MusicViewModelFactory(
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val songStorage: SongStorage,
    private val playbackStateStore: PlaybackStateStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(repository, playerManager, songStorage, playbackStateStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
