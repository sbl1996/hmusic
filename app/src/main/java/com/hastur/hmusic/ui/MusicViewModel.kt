package com.hastur.hmusic.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hastur.hmusic.data.BackupProfileEntity
import com.hastur.hmusic.data.MusicdlDownloadEntity
import com.hastur.hmusic.data.MusicdlPendingDownloadEntity
import com.hastur.hmusic.data.MusicRepository
import com.hastur.hmusic.data.RemoteSongEntity
import com.hastur.hmusic.data.SongEntity
import com.hastur.hmusic.sync.CloudSyncService
import com.hastur.hmusic.player.LoopMode
import com.hastur.hmusic.player.MusicPlayerManager
import com.hastur.hmusic.player.PlaybackStateStore
import com.hastur.hmusic.search.MusicdlApiClient
import com.hastur.hmusic.search.MusicdlDownloadResult
import com.hastur.hmusic.search.MusicdlSearchItem
import com.hastur.hmusic.search.MusicdlSearchResponse
import com.hastur.hmusic.search.MusicdlSettingsStore
import com.hastur.hmusic.sync.OssClient
import com.hastur.hmusic.sync.OssClientFactory
import com.hastur.hmusic.sync.SongStorage
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

    val canBackup: Boolean
        get() = localSong?.isDownloaded == true && remoteSong == null
}

sealed class StatusMessageState {
    object Idle : StatusMessageState()
    object Loading : StatusMessageState()
    data class Completed(val message: String) : StatusMessageState()
    data class Error(val message: String) : StatusMessageState()
}

enum class SongTransferDirection {
    DOWNLOAD,
    UPLOAD
}

sealed class SongTransferState {
    object Idle : SongTransferState()
    data class Running(
        val direction: SongTransferDirection,
        val bytesRead: Long,
        val totalBytes: Long?
    ) : SongTransferState() {
        val progress: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { bytesRead.toFloat() / it.toFloat() }
    }

    data class Failed(
        val direction: SongTransferDirection,
        val message: String
    ) : SongTransferState()
}

data class MusicdlSearchUiState(
    val keyword: String = "",
    val searchId: String? = null,
    val sessionId: String? = null,
    val items: List<MusicdlSearchItem> = emptyList(),
    val downloadedItemMd5ByKey: Map<String, String> = emptyMap(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val lastSources: List<String> = emptyList()
)

data class LocalScanState(
    val isScanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModel(
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val songStorage: SongStorage,
    private val playbackStateStore: PlaybackStateStore,
    private val cloudSyncService: CloudSyncService,
    private val ossClientFactory: OssClientFactory,
    private val musicdlApiClient: MusicdlApiClient,
    private val musicdlSettingsStore: MusicdlSettingsStore
) : ViewModel() {
    private val _statusMessageState = MutableStateFlow<StatusMessageState>(StatusMessageState.Idle)
    val statusMessageState: StateFlow<StatusMessageState> = _statusMessageState.asStateFlow()

    private val _transferStates = MutableStateFlow<Map<String, SongTransferState>>(emptyMap())
    val transferStates: StateFlow<Map<String, SongTransferState>> = _transferStates.asStateFlow()

    private val _musicdlSearchState = MutableStateFlow(MusicdlSearchUiState())
    val musicdlSearchState: StateFlow<MusicdlSearchUiState> = _musicdlSearchState.asStateFlow()

    private val _localScanState = MutableStateFlow(LocalScanState())
    val localScanState: StateFlow<LocalScanState> = _localScanState.asStateFlow()

    val musicdlBaseUrl: StateFlow<String> = musicdlSettingsStore.baseUrlFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MusicdlSettingsStore.DEFAULT_BASE_URL
        )

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
                _transferStates.value = emptyMap()
                ossClient = ossClientFactory.create(profile)
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

    fun updateMusicdlKeyword(keyword: String) {
        _musicdlSearchState.update { it.copy(keyword = keyword, error = null) }
    }

    fun updateMusicdlBaseUrl(baseUrl: String) {
        viewModelScope.launch {
            musicdlSettingsStore.saveBaseUrl(baseUrl)
        }
    }

    fun searchMusicdl() {
        val keyword = musicdlSearchState.value.keyword.trim()
        if (keyword.isBlank()) {
            _musicdlSearchState.update { it.copy(error = "请输入搜索关键词") }
            return
        }
        val baseUrl = musicdlBaseUrl.value
        _musicdlSearchState.update { it.copy(isSearching = true, searchId = null, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var task = musicdlApiClient.createSearch(baseUrl = baseUrl, keyword = keyword)
                _musicdlSearchState.update { it.copy(searchId = task.searchId) }
                while (isActive && !task.isTerminal) {
                    delay(1000)
                    task = musicdlApiClient.getSearch(baseUrl = baseUrl, searchId = task.searchId)
                    _musicdlSearchState.update { it.copy(searchId = task.searchId) }
                }
                if (task.status == "failed") {
                    error(task.error ?: "musicdl 搜索失败")
                }
                val response = task.result ?: error("musicdl 搜索结果为空")
                val downloadedItemMd5ByKey = resolveDownloadedMusicdlItems(response.items)
                _musicdlSearchState.value = response.toUiState(downloadedItemMd5ByKey)
                _statusMessageState.value = StatusMessageState.Completed("找到 ${response.itemCount} 首在线歌曲")
            } catch (e: Exception) {
                val message = e.localizedMessage ?: "未知错误"
                _musicdlSearchState.update { it.copy(isSearching = false, error = message) }
                _statusMessageState.value = StatusMessageState.Error("搜索失败: $message")
            }
        }
    }

    fun downloadMusicdlItem(item: MusicdlSearchItem) {
        val downloadedMd5 = musicdlSearchState.value.downloadedItemMd5ByKey[musicdlItemStableKey(item)]
        if (!downloadedMd5.isNullOrBlank()) {
            playDownloadedMusicdlItem(downloadedMd5)
            return
        }

        val sessionId = musicdlSearchState.value.sessionId
        if (sessionId.isNullOrBlank()) {
            _statusMessageState.value = StatusMessageState.Error("搜索会话已失效，请重新搜索")
            return
        }

        val transferKey = musicdlTransferKey(sessionId, item.itemId)
        if (_transferStates.value[transferKey] is SongTransferState.Running) {
            return
        }
        _transferStates.update {
            it + (
                transferKey to SongTransferState.Running(
                    SongTransferDirection.DOWNLOAD,
                    bytesRead = 0L,
                    totalBytes = item.fileSizeBytes
                )
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = musicdlBaseUrl.value
                val stableKey = musicdlItemStableKey(item)
                var remoteName = musicdlStorageName(item, null)
                val pendingDownload = repository.getMusicdlPendingDownload(stableKey)
                var task = pendingDownload?.let { pending ->
                    runCatching {
                        remoteName = pending.remoteName
                        musicdlApiClient.getDownload(baseUrl, pending.taskId)
                    }.getOrNull()
                } ?: musicdlApiClient.createDownload(baseUrl, sessionId, item.itemId).also { created ->
                    remoteName = musicdlStorageName(item, created.result)
                    repository.upsertMusicdlPendingDownload(
                        MusicdlPendingDownloadEntity(
                            stableKey = stableKey,
                            taskId = created.taskId,
                            sessionId = sessionId,
                            itemId = item.itemId,
                            remoteName = remoteName
                        )
                    )
                }
                if (pendingDownload != null && pendingDownload.taskId == task.taskId) {
                    repository.upsertMusicdlPendingDownload(pendingDownload.copy(updatedAt = System.currentTimeMillis()))
                }
                while (isActive && !task.isTerminal) {
                    _transferStates.update {
                        it + (
                            transferKey to SongTransferState.Running(
                                SongTransferDirection.DOWNLOAD,
                                bytesRead = task.progress.downloadedBytes,
                                totalBytes = task.progress.totalBytes ?: item.fileSizeBytes
                            )
                        )
                    }
                    delay(800)
                    task = musicdlApiClient.getDownload(baseUrl, task.taskId)
                }

                if (task.status == "failed") {
                    repository.deleteMusicdlPendingDownload(stableKey)
                    error(task.error ?: "musicdl 下载失败")
                }
                if (task.status != "completed") {
                    error("musicdl 下载未完成")
                }

                remoteName = musicdlStorageName(item, task.result)
                repository.upsertMusicdlPendingDownload(
                    MusicdlPendingDownloadEntity(
                        stableKey = stableKey,
                        taskId = task.taskId,
                        sessionId = sessionId,
                        itemId = item.itemId,
                        remoteName = remoteName
                    )
                )
                val partialBytes = songStorage.partialRemoteBytes(remoteName)
                val downloadTitle = item.songName?.trim().orEmpty()
                    .ifBlank { task.result?.songName?.trim().orEmpty() }
                    .ifBlank { remoteName.substringBeforeLast(".") }
                val downloadArtist = item.singers?.trim().orEmpty()
                    .ifBlank { task.result?.singers?.trim().orEmpty() }
                    .ifBlank { "佚名" }
                val stored = musicdlApiClient.downloadFile(
                    baseUrl = baseUrl,
                    taskId = task.taskId,
                    rangeStart = partialBytes.takeIf { it > 0L }
                ) { input, contentLength, isPartial ->
                    val append = isPartial && partialBytes > 0L
                    val totalBytes = if (append) {
                        contentLength?.let { partialBytes + it } ?: item.fileSizeBytes
                    } else {
                        contentLength ?: item.fileSizeBytes
                    }
                    val expectedBytes = contentLength?.let {
                        if (append) partialBytes + it else it
                    }
                    songStorage.storeRemoteStreamResumable(
                        remoteKey = remoteName,
                        inputStream = input,
                        title = downloadTitle,
                        artist = downloadArtist,
                        totalBytes = totalBytes,
                        expectedBytes = expectedBytes,
                        append = append
                    ) { bytesRead, total ->
                        _transferStates.update {
                            it + (
                                transferKey to SongTransferState.Running(
                                    SongTransferDirection.DOWNLOAD,
                                    bytesRead = bytesRead,
                                    totalBytes = total
                                )
                            )
                        }
                    }
                }

                val existing = repository.findLocalSongByMd5(stored.md5sum)
                val song = SongEntity(
                    id = existing?.id ?: 0,
                    md5sum = stored.md5sum,
                    title = downloadTitle
                        .ifBlank { existing?.title ?: stored.fileName.substringBeforeLast(".") },
                    artist = downloadArtist
                        .ifBlank { existing?.artist ?: "佚名" },
                    durationMs = resolveImportedSongDuration(
                        filePath = stored.localPath,
                        fallbackDurationMs = item.durationSeconds?.times(1000)
                            ?: existing?.durationMs
                            ?: 0L
                    ),
                    fileName = stored.fileName,
                    mimeType = stored.mimeType,
                    localPath = stored.localPath,
                    localUpdatedAt = stored.updatedAt,
                    syncTime = System.currentTimeMillis()
                )
                repository.insertLocalSong(song)
                repository.insertMusicdlDownload(
                    MusicdlDownloadEntity(
                        stableKey = stableKey,
                        md5sum = song.md5sum,
                        source = item.source.orEmpty(),
                        songName = item.songName.orEmpty(),
                        singers = item.singers.orEmpty(),
                        fileSizeBytes = item.fileSizeBytes,
                        fileSize = item.fileSize.orEmpty(),
                        extension = item.extension.orEmpty(),
                        durationSeconds = item.durationSeconds,
                        duration = item.duration.orEmpty(),
                        identifier = item.identifier.orEmpty(),
                        album = item.album.orEmpty(),
                        coverUrl = item.coverUrl.orEmpty()
                    )
                )
                repository.deleteMusicdlPendingDownload(stableKey)
                _musicdlSearchState.update { state ->
                    state.copy(downloadedItemMd5ByKey = state.downloadedItemMd5ByKey + (stableKey to song.md5sum))
                }
                _transferStates.update { it - transferKey }
                _statusMessageState.value = StatusMessageState.Completed("已下载：${song.title}")
                withContext(Dispatchers.Main) {
                    playerManager.playSong(song)
                    persistPlaybackState()
                }
            } catch (e: Exception) {
                val message = e.localizedMessage ?: "未知错误"
                _transferStates.update {
                    it + (
                        transferKey to SongTransferState.Failed(
                            SongTransferDirection.DOWNLOAD,
                            message
                        )
                    )
                }
                _statusMessageState.value = StatusMessageState.Error("在线下载失败: $message")
                delay(2500)
                _transferStates.update { states ->
                    if (states[transferKey] is SongTransferState.Failed) states - transferKey else states
                }
            }
        }
    }

    private fun playDownloadedMusicdlItem(md5sum: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val localSong = repository.findLocalSongByMd5(md5sum)
            if (localSong == null || !localSong.isDownloaded) {
                _statusMessageState.value = StatusMessageState.Error("本地文件不可用，请重新下载")
                return@launch
            }
            withContext(Dispatchers.Main) {
                shouldShowPlayerError.value = true
                playerManager.playSong(localSong)
                persistPlaybackState()
            }
        }
    }

    fun downloadSong(song: LibrarySongItem) {
        val client = ossClient
        val remoteSong = song.remoteSong
        if (client == null || remoteSong == null) {
            _statusMessageState.value = StatusMessageState.Error("当前歌曲没有可用的云端备份")
            return
        }
        val songKey = remoteSong.md5sum.ifBlank { song.md5sum }
        if (_transferStates.value[songKey] is SongTransferState.Running) {
            return
        }
        _transferStates.update {
            it + (songKey to SongTransferState.Running(SongTransferDirection.DOWNLOAD, bytesRead = 0L, totalBytes = null))
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = cloudSyncService.downloadSong(
                    song = remoteSong,
                    existingLocalSong = song.localSong,
                    client = client,
                    onProgress = { bytesRead, totalBytes ->
                        _transferStates.update { states ->
                            states + (
                                songKey to SongTransferState.Running(
                                    SongTransferDirection.DOWNLOAD,
                                    bytesRead,
                                    totalBytes
                                )
                            )
                        }
                    }
                )
                _transferStates.update { it - songKey }
                _statusMessageState.value = StatusMessageState.Completed("已同步到本地：${result.songTitle}")
            } catch (e: Exception) {
                _transferStates.update {
                    it + (
                        songKey to SongTransferState.Failed(
                            SongTransferDirection.DOWNLOAD,
                            e.localizedMessage ?: "未知错误"
                        )
                    )
                }
                _statusMessageState.value = StatusMessageState.Error("下载歌曲失败: ${e.localizedMessage}")
                delay(2500)
                _transferStates.update { states ->
                    if (states[songKey] is SongTransferState.Failed) states - songKey else states
                }
            }
        }
    }

    fun uploadSong(song: LibrarySongItem) {
        val profile = activeProfile.value
        val client = ossClient
        val localSong = song.localSong
        if (profile == null || client == null) {
            _statusMessageState.value = StatusMessageState.Error("请先配置有效的 S3 / OSS Profile")
            return
        }
        if (localSong == null || !localSong.isDownloaded) {
            _statusMessageState.value = StatusMessageState.Error("当前歌曲未下载到本地")
            return
        }
        val songKey = localSong.md5sum.ifBlank { song.md5sum }
        if (_transferStates.value[songKey] is SongTransferState.Running) {
            return
        }
        _transferStates.update {
            it + (songKey to SongTransferState.Running(SongTransferDirection.UPLOAD, bytesRead = 0L, totalBytes = localSongFileSize(localSong)))
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localFileSize = songStorage.size(localSong.localPath) ?: 0L
                _transferStates.update {
                    it + (
                        songKey to SongTransferState.Running(
                            SongTransferDirection.UPLOAD,
                            bytesRead = localFileSize,
                            totalBytes = localFileSize
                        )
                    )
                }
                val result = cloudSyncService.uploadSong(
                    profile = profile,
                    client = client,
                    localSong = localSong,
                    currentRemoteSongs = remoteSongs.value
                )
                _transferStates.update { it - songKey }
                _statusMessageState.value = StatusMessageState.Completed("已上传到云端：${result.songTitle}")
            } catch (e: Exception) {
                _transferStates.update {
                    it + (
                        songKey to SongTransferState.Failed(
                            SongTransferDirection.UPLOAD,
                            e.localizedMessage ?: "未知错误"
                        )
                    )
                }
                _statusMessageState.value = StatusMessageState.Error("上传歌曲失败: ${e.localizedMessage}")
                delay(2500)
                _transferStates.update { states ->
                    if (states[songKey] is SongTransferState.Failed) states - songKey else states
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
                nextProfileName(backupProfiles.value, "新配置")
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
                val result = cloudSyncService.syncFromCloud(profile, client)

                val downloadedCount = localSongs.value.count { it.isDownloaded }
                _statusMessageState.value = StatusMessageState.Completed(
                    "已同步 ${profile.name}：云端 ${result.remoteSongCount} 首，本地 ${downloadedCount} 首"
                )
            } catch (e: Exception) {
                _statusMessageState.value = StatusMessageState.Error("同步歌单失败: ${e.localizedMessage}")
            }
        }
    }

    fun addLocalSong(title: String, artist: String, uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val normalizedTitle = title.trim()
                val normalizedArtist = artist.trim()
                val stored = songStorage.importFromUri(
                    uriString = uri,
                    title = normalizedTitle,
                    artist = normalizedArtist
                )
                val existing = repository.findLocalSongByMd5(stored.md5sum)
                val song = SongEntity(
                    id = existing?.id ?: 0,
                    md5sum = stored.md5sum,
                    title = normalizedTitle.ifBlank { existing?.title ?: stored.fileName.substringBeforeLast(".") },
                    artist = normalizedArtist.ifBlank { existing?.artist ?: "佚名" },
                    durationMs = resolveImportedSongDuration(
                        filePath = stored.localPath,
                        fallbackDurationMs = existing?.durationMs ?: 0L
                    ),
                    fileName = stored.fileName,
                    mimeType = stored.mimeType,
                    localPath = stored.localPath,
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

    fun scanAndRestoreLocalSongs() {
        if (_localScanState.value.isScanning) return
        if (!songStorage.hasConfiguredDirectory()) {
            _statusMessageState.value = StatusMessageState.Error("请先选择歌曲存储目录")
            return
        }

        _localScanState.value = LocalScanState(isScanning = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localSnapshot = localSongs.value
                val existingMd5 = localSnapshot.mapTo(mutableSetOf()) { it.md5sum }
                val knownLocalPaths = localSnapshot
                    .mapNotNullTo(mutableSetOf()) { song ->
                        song.localPath.takeIf(String::isNotBlank)
                    }
                val remoteByMd5 = remoteSongs.value.associateBy { it.md5sum }
                var restoredCount = 0
                var restoredFromCloudCount = 0
                val scannedFiles = songStorage.scanConfiguredDirectory(
                    knownLocalPaths = knownLocalPaths,
                    onProgress = { scanned, total ->
                        _localScanState.value = LocalScanState(
                            isScanning = true,
                            scanned = scanned,
                            total = total
                        )
                    }
                )

                scannedFiles.forEach { scanned ->
                    if (!existingMd5.add(scanned.md5sum)) return@forEach
                    val remote = remoteByMd5[scanned.md5sum]
                    val parsedName = parseReadableSongFileName(scanned.fileName)
                    repository.insertLocalSong(
                        SongEntity(
                            md5sum = scanned.md5sum,
                            title = remote?.title?.takeIf(String::isNotBlank)
                                ?: scanned.embeddedTitle
                                ?: parsedName.second,
                            artist = remote?.artist?.takeIf(String::isNotBlank)
                                ?: scanned.embeddedArtist
                                ?: parsedName.first,
                            durationMs = remote?.durationMs?.takeIf { it > 0 }
                                ?: scanned.durationMs,
                            fileName = scanned.fileName,
                            mimeType = scanned.mimeType,
                            localPath = scanned.localPath,
                            localUpdatedAt = scanned.updatedAt,
                            syncTime = System.currentTimeMillis()
                        )
                    )
                    restoredCount += 1
                    if (remote != null) restoredFromCloudCount += 1
                }

                _statusMessageState.value = if (restoredCount == 0) {
                    StatusMessageState.Completed("扫描完成，没有发现需要恢复的歌曲")
                } else {
                    StatusMessageState.Completed(
                        "已恢复 $restoredCount 首歌曲，其中 $restoredFromCloudCount 首使用云端信息"
                    )
                }
            } catch (e: Exception) {
                _statusMessageState.value =
                    StatusMessageState.Error("扫描本地歌曲失败：${e.localizedMessage}")
            } finally {
                _localScanState.value = LocalScanState()
            }
        }
    }

    fun deleteSong(song: LibrarySongItem, deleteLocalFile: Boolean = false) {
        val localSong = song.localSong ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteLocalFile) {
                if (songStorage.exists(localSong.localPath) && !songStorage.delete(localSong.localPath)) {
                    _statusMessageState.value = StatusMessageState.Error("删除本地文件失败：${localSong.fileName}")
                    return@launch
                }
            }
            repository.deleteLocalSong(localSong)
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

    private fun localSongFileSize(song: SongEntity): Long? {
        return songStorage.size(song.localPath)?.takeIf { it > 0L }
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

    private fun parseReadableSongFileName(fileName: String): Pair<String, String> {
        val baseName = fileName.substringBeforeLast(".")
        val separatorIndex = baseName.indexOf(" - ")
        if (separatorIndex < 0) {
            return "佚名" to baseName.ifBlank { "未知歌曲" }
        }
        val artist = baseName.substring(0, separatorIndex).trim().ifBlank { "佚名" }
        val title = baseName.substring(separatorIndex + 3).trim().ifBlank { "未知歌曲" }
        return artist to title
    }

    private fun MusicdlSearchResponse.toUiState(
        downloadedItemMd5ByKey: Map<String, String>
    ): MusicdlSearchUiState {
        return MusicdlSearchUiState(
            keyword = keyword,
            searchId = null,
            sessionId = sessionId,
            items = items,
            downloadedItemMd5ByKey = downloadedItemMd5ByKey,
            isSearching = false,
            error = null,
            lastSources = sources
        )
    }

    private suspend fun resolveDownloadedMusicdlItems(
        items: List<MusicdlSearchItem>
    ): Map<String, String> {
        val stableKeys = items.map(::musicdlItemStableKey).distinct()
        val downloads = repository.getMusicdlDownloadsByStableKeys(stableKeys)
        val downloaded = mutableMapOf<String, String>()
        val staleKeys = mutableListOf<String>()

        downloads.forEach { download ->
            val localSong = repository.findLocalSongByMd5(download.md5sum)
            if (localSong?.isDownloaded == true) {
                downloaded[download.stableKey] = download.md5sum
            } else {
                staleKeys += download.stableKey
            }
        }

        repository.deleteMusicdlDownloadsByStableKeys(staleKeys)
        return downloaded
    }

    private fun musicdlItemStableKey(item: MusicdlSearchItem): String {
        return listOf(
            item.source,
            item.songName,
            item.singers,
            item.fileSizeBytes?.toString() ?: item.fileSize,
            item.extension,
            item.durationSeconds?.toString() ?: item.duration
        ).joinToString("|") { it.orEmpty().trim().lowercase() }
    }

    private fun musicdlTransferKey(sessionId: String, itemId: String): String {
        return "musicdl:$sessionId:$itemId"
    }

    private fun musicdlStorageName(item: MusicdlSearchItem, result: MusicdlDownloadResult?): String {
        val title = item.songName?.trim().orEmpty()
            .ifBlank { result?.songName?.trim().orEmpty() }
            .ifBlank { "track" }
        val artist = item.singers?.trim().orEmpty()
            .ifBlank { result?.singers?.trim().orEmpty() }
        val extension = item.extension?.trim()?.trimStart('.').orEmpty()
            .ifBlank { result?.extension?.trim()?.trimStart('.').orEmpty() }
            .ifBlank { result?.savePath?.substringAfterLast('.', "")?.trim().orEmpty() }
            .ifBlank { "mp3" }
        val baseName = if (artist.isBlank()) title else "$artist - $title"
        return "$baseName.$extension"
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
        return songStorage.durationMs(filePath)
    }

    fun hasSongDirectory(): Boolean = songStorage.hasConfiguredDirectory()

    fun configureSongDirectory(uri: Uri) {
        songStorage.configureDirectory(uri)
        _statusMessageState.value = StatusMessageState.Completed("歌曲存储目录已设置")
    }
}

class MusicViewModelFactory(
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val songStorage: SongStorage,
    private val playbackStateStore: PlaybackStateStore,
    private val cloudSyncService: CloudSyncService,
    private val ossClientFactory: OssClientFactory,
    private val musicdlApiClient: MusicdlApiClient,
    private val musicdlSettingsStore: MusicdlSettingsStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(
                repository,
                playerManager,
                songStorage,
                playbackStateStore,
                cloudSyncService,
                ossClientFactory,
                musicdlApiClient,
                musicdlSettingsStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
