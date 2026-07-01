package com.hastur.hmusic.ui

import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hastur.hmusic.data.BackupProfileEntity
import com.hastur.hmusic.data.MusicRepository
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

sealed class StatusMessageState {
    object Idle : StatusMessageState()
    object Loading : StatusMessageState()
    data class Completed(val message: String) : StatusMessageState()
    data class Error(val message: String) : StatusMessageState()
}

sealed class SongDownloadState {
    object Idle : SongDownloadState()
    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long?
    ) : SongDownloadState() {
        val progress: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { bytesRead.toFloat() / it.toFloat() }
    }

    data class Failed(val message: String) : SongDownloadState()
}

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val _statusMessageState = MutableStateFlow<StatusMessageState>(StatusMessageState.Idle)
    val statusMessageState: StateFlow<StatusMessageState> = _statusMessageState.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, SongDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, SongDownloadState>> = _downloadStates.asStateFlow()

    val localSongs: StateFlow<List<SongEntity>> = repository.localSongs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val backupProfiles: StateFlow<List<BackupProfileEntity>> = repository.backupProfilesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeProfile: StateFlow<BackupProfileEntity?> = repository.activeBackupProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val remoteSongs: StateFlow<List<RemoteSongEntity>> = activeProfile
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(emptyList())
            } else {
                repository.getRemoteSongsFlow(profile.id)
            }
        }
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

    val currentSong: StateFlow<SongEntity?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val loopMode: StateFlow<LoopMode> = playerManager.loopMode
    private val shouldShowPlayerError = MutableStateFlow(false)
    val playerError: StateFlow<String?> = combine(
        playerManager.errorMessage,
        shouldShowPlayerError
    ) { error, shouldShow ->
        if (shouldShow) error else null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private var ossClient: OssClient? = null

    init {
        viewModelScope.launch {
            ensureDefaultBackupProfile()
        }

        viewModelScope.launch(Dispatchers.IO) {
            restorePlaybackState()
        }

        viewModelScope.launch {
            activeProfile.collect { profile ->
                ossClient?.close()
                _downloadStates.value = emptyMap()
                ossClient = if (profile != null && profile.endpoint.isNotEmpty()) {
                    OssClient(
                        endpoint = profile.endpoint,
                        region = profile.region.ifBlank { OssClient.defaultRegion(profile.endpoint) },
                        forcePathStyle = profile.forcePathStyle,
                        bucket = profile.bucket,
                        accessKeyId = profile.accessKeyId,
                        accessKeySecret = profile.accessKeySecret,
                        prefix = profile.prefix
                    )
                } else {
                    null
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(playerManager.currentSong, playerManager.duration) { song, durationMs ->
                song to durationMs
            }.distinctUntilChanged().collect { (song, durationMs) ->
                persistResolvedDuration(song, durationMs)
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
        shouldShowPlayerError.value = true
        val localSong = song.localSong
        if (localSong == null || !localSong.isDownloaded) {
            _statusMessageState.value = StatusMessageState.Error("当前歌曲未下载到本地")
            return
        }
        playerManager.playSong(localSong)
        persistPlaybackState()
    }

    fun downloadSong(song: LibrarySongItem) {
        val client = ossClient
        val remoteSong = song.remoteSong
        if (client == null || remoteSong == null) {
            _statusMessageState.value = StatusMessageState.Error("当前歌曲没有可用的云端备份")
            return
        }
        val songKey = remoteSong.md5sum.ifBlank { song.md5sum }
        if (_downloadStates.value[songKey] is SongDownloadState.Downloading) {
            return
        }
        _downloadStates.update { it + (songKey to SongDownloadState.Downloading(bytesRead = 0L, totalBytes = null)) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remoteStream = client.downloadSongStream(remoteSong.remoteKey)
                val stored = remoteStream.inputStream.use {
                    songStorage.storeRemoteStream(
                        remoteKey = remoteSong.remoteKey,
                        inputStream = it,
                        totalBytes = remoteStream.contentLength,
                        onProgress = { bytesRead, totalBytes ->
                            _downloadStates.update { states ->
                                states + (songKey to SongDownloadState.Downloading(bytesRead, totalBytes))
                            }
                        }
                    )
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
                _downloadStates.update { it - songKey }
                _statusMessageState.value = StatusMessageState.Completed("已同步到本地：${local.title}")
            } catch (e: Exception) {
                _downloadStates.update {
                    it + (songKey to SongDownloadState.Failed(e.localizedMessage ?: "未知错误"))
                }
                _statusMessageState.value = StatusMessageState.Error("下载歌曲失败: ${e.localizedMessage}")
                delay(2500)
                _downloadStates.update { states ->
                    if (states[songKey] is SongDownloadState.Failed) states - songKey else states
                }
            }
        }
    }

    fun playOrPause() {
        shouldShowPlayerError.value = true
        playerManager.playOrPause()
        persistPlaybackState()
    }

    fun playNext() {
        shouldShowPlayerError.value = true
        playerManager.playNext()
        persistPlaybackState()
    }

    fun playPrevious() {
        shouldShowPlayerError.value = true
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

    fun resetStatusMessage() {
        _statusMessageState.value = StatusMessageState.Idle
    }

    fun showStatusCompleted(message: String) {
        _statusMessageState.value = StatusMessageState.Completed(message)
    }

    fun showStatusError(message: String) {
        _statusMessageState.value = StatusMessageState.Error(message)
    }

    fun createProfile(copyCurrent: Boolean = false) {
        viewModelScope.launch {
            val existingProfiles = backupProfiles.value
            val source = if (copyCurrent) activeProfile.value else null
            val now = System.currentTimeMillis()
            val newProfile = (source ?: BackupProfileEntity()).copy(
                id = 0,
                name = nextProfileName(
                    existingProfiles = existingProfiles,
                    baseName = if (copyCurrent && source != null) "${source.name} 副本" else "配置"
                ),
                isActive = true,
                createdAt = now,
                updatedAt = now,
                lastSyncAt = 0
            )
            val profileId = repository.insertBackupProfile(newProfile)
            repository.activateBackupProfile(profileId)
            _statusMessageState.value = StatusMessageState.Completed("已创建配置：${newProfile.name}")
        }
    }

    fun switchProfile(profileId: Long) {
        viewModelScope.launch {
            val profile = repository.getBackupProfile(profileId) ?: return@launch
            repository.activateBackupProfile(profileId)
            _statusMessageState.value = StatusMessageState.Completed("已切换到配置：${profile.name}")
        }
    }

    fun deleteActiveProfile() {
        viewModelScope.launch {
            val current = activeProfile.value
            val profiles = backupProfiles.value
            if (current == null) {
                _statusMessageState.value = StatusMessageState.Error("当前没有可删除的配置")
                return@launch
            }
            if (profiles.size <= 1) {
                _statusMessageState.value = StatusMessageState.Error("至少保留一个配置")
                return@launch
            }

            val nextProfile = profiles.firstOrNull { it.id != current.id }
            repository.clearRemoteSongsByProfileId(current.id)
            repository.deleteBackupProfile(current.id)
            if (nextProfile != null) {
                repository.activateBackupProfile(nextProfile.id)
            }
            _statusMessageState.value = StatusMessageState.Completed("已删除配置：${current.name}")
        }
    }

    fun saveActiveProfile(
        name: String,
        endpoint: String,
        region: String,
        forcePathStyle: Boolean,
        bucket: String,
        accessKeyId: String,
        accessKeySecret: String,
        prefix: String
    ) {
        viewModelScope.launch {
            val current = activeProfile.value
            val now = System.currentTimeMillis()
            val normalizedEndpoint = endpoint.trim()
            val normalizedName = name.trim().ifEmpty {
                nextProfileName(backupProfiles.value, "配置")
            }

            val profile = BackupProfileEntity(
                id = current?.id ?: 0,
                name = normalizedName,
                endpoint = normalizedEndpoint,
                region = region.trim().ifEmpty { OssClient.defaultRegion(normalizedEndpoint) },
                forcePathStyle = forcePathStyle,
                bucket = bucket.trim(),
                accessKeyId = accessKeyId.trim(),
                accessKeySecret = accessKeySecret.trim(),
                prefix = prefix.trim(),
                isActive = true,
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                lastSyncAt = current?.lastSyncAt ?: 0
            )

            val profileId = repository.insertBackupProfile(profile)
            repository.activateBackupProfile(profileId)
            _statusMessageState.value = StatusMessageState.Completed("配置已保存：$normalizedName")
            delay(500)
            syncFromOSS()
        }
    }

    fun syncFromOSS() {
        val profile = activeProfile.value
        val client = ossClient
        if (profile == null || client == null) {
            _statusMessageState.value = StatusMessageState.Error("请先配置有效的 S3 / OSS Profile")
            return
        }
        _statusMessageState.value = StatusMessageState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val manifest = loadManifest(client)
                val remoteEntities = manifest?.songs?.map { it.toRemoteSongEntity(profile.id) }.orEmpty()
                repository.replaceRemoteSongs(profile.id, remoteEntities)
                touchProfileLastSync(profile)

                val downloadedCount = localSongs.value.count { it.isDownloaded }
                _statusMessageState.value = StatusMessageState.Completed(
                    "已同步 ${profile.name}：云端 ${remoteEntities.size} 首，本地 ${downloadedCount} 首"
                )
            } catch (e: Exception) {
                _statusMessageState.value = StatusMessageState.Error("同步歌单失败: ${e.localizedMessage}")
            }
        }
    }

    fun backupPlaylistToOSS() {
        val profile = activeProfile.value
        val client = ossClient
        if (profile == null || client == null) {
            _statusMessageState.value = StatusMessageState.Error("请先配置 S3 / OSS Profile 后再上传备份")
            return
        }
        _statusMessageState.value = StatusMessageState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val syncedRemoteSongs = localSongs.value
                    .mapNotNull { normalizeLocalSong(it) }
                    .map { localSong ->
                        val remoteKey = client.buildRemoteAudioKey(
                            localSong.md5sum,
                            localSong.fileName.ifBlank { File(localSong.localPath).name }
                        )
                        client.uploadSongFile(remoteKey, File(localSong.localPath), localSong.mimeType)
                        RemoteSongEntity(
                            profileId = profile.id,
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

                repository.replaceRemoteSongs(profile.id, syncedRemoteSongs)
                touchProfileLastSync(profile, now)
                _statusMessageState.value = StatusMessageState.Completed("已备份到 ${profile.name}，歌单索引已刷新")
            } catch (e: Exception) {
                _statusMessageState.value = StatusMessageState.Error("上传备份失败: ${e.localizedMessage}")
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
                    durationMs = resolveImportedSongDuration(
                        filePath = stored.file.absolutePath,
                        fallbackDurationMs = existing?.durationMs ?: 0L
                    ),
                    fileName = stored.fileName,
                    mimeType = stored.mimeType,
                    localPath = stored.file.absolutePath,
                    localUpdatedAt = stored.updatedAt,
                    syncTime = System.currentTimeMillis()
                )
                repository.insertLocalSong(song)
                _statusMessageState.value = StatusMessageState.Completed("已导入本地歌曲：${song.title}")
                viewModelScope.launch {
                    playerManager.playSong(song)
                    persistPlaybackState()
                }
            } catch (e: Exception) {
                _statusMessageState.value = StatusMessageState.Error("导入本地歌曲失败: ${e.localizedMessage}")
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

    private suspend fun ensureDefaultBackupProfile() {
        try {
            if (repository.countBackupProfiles() > 0) return

            val now = System.currentTimeMillis()
            val profile = BackupProfileEntity(
                name = "默认配置",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            val profileId = repository.insertBackupProfile(profile)
            repository.activateBackupProfile(profileId)
            _statusMessageState.value = StatusMessageState.Completed("已创建默认云端配置")
        } catch (_: Exception) {
            // Ignore profile bootstrapping errors.
        }
    }

    private suspend fun touchProfileLastSync(profile: BackupProfileEntity, syncedAt: Long = System.currentTimeMillis()) {
        repository.updateBackupProfile(
            profile.copy(
                lastSyncAt = syncedAt,
                updatedAt = syncedAt,
                isActive = true
            )
        )
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
            playerManager.restoreSong(localSong, savedState.currentPositionMs) {
                viewModelScope.launch(Dispatchers.IO) {
                    playbackStateStore.clear()
                }
            }
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

    private fun nextProfileName(existingProfiles: List<BackupProfileEntity>, baseName: String): String {
        val existingNames = existingProfiles.map { it.name }.toSet()
        if (baseName !in existingNames) return baseName
        var index = 2
        while (true) {
            val candidate = "$baseName $index"
            if (candidate !in existingNames) return candidate
            index++
        }
    }

    private suspend fun persistResolvedDuration(song: SongEntity?, durationMs: Long) {
        val resolvedSong = song ?: return
        val resolvedDurationMs = durationMs.coerceAtLeast(0L)
        if (resolvedDurationMs <= 0L) return
        if (resolvedSong.durationMs == resolvedDurationMs) return

        repository.updateLocalSongDuration(resolvedSong.md5sum, resolvedDurationMs)
        repository.updateRemoteSongDuration(resolvedSong.md5sum, resolvedDurationMs)
    }

    private fun resolveImportedSongDuration(filePath: String, fallbackDurationMs: Long): Long {
        val extractedDurationMs = extractDurationMs(filePath)
        return extractedDurationMs.takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(0L)
    }

    private fun extractDurationMs(filePath: String): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(0L)
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
    fun toRemoteSongEntity(profileId: Long): RemoteSongEntity {
        return RemoteSongEntity(
            profileId = profileId,
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
