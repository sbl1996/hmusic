package com.hastur.hmusic.player

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.hastur.hmusic.data.SongEntity
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LoopMode {
    LIST, SINGLE
}

class MusicPlayerManager(private val context: Context) {
    data class PlaybackSnapshot(
        val song: SongEntity?,
        val positionMs: Long,
        val isPlaying: Boolean
    )

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private val tag = "MusicPlayerManager"
    private var pendingSeekPositionMs: Long = 0L
    private var pendingAutoPlay: Boolean = true
    private var suppressLoadErrorMessage: Boolean = false
    private var pendingLoadFailureAction: (() -> Unit)? = null

    // Exposed Playback States
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _playlist = MutableStateFlow<List<SongEntity>>(emptyList())
    val playlist: StateFlow<List<SongEntity>> = _playlist.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _loopMode = MutableStateFlow(LoopMode.LIST)
    val loopMode: StateFlow<LoopMode> = _loopMode.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        setupMediaPlayer()
    }

    private fun setupMediaPlayer() {
        releasePlayer()
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { mp ->
                _duration.value = mp.duration.toLong()
                val durationMs = mp.duration.toLong().coerceAtLeast(0L)
                val safeSeekPosition = pendingSeekPositionMs
                    .coerceAtLeast(0L)
                    .coerceAtMost((durationMs - 1000L).coerceAtLeast(0L))
                if (safeSeekPosition > 0L) {
                    mp.seekTo(safeSeekPosition.toInt())
                }
                _currentPosition.value = safeSeekPosition
                if (pendingAutoPlay) {
                    mp.start()
                    _isPlaying.value = true
                    startProgressTracker()
                } else {
                    _isPlaying.value = false
                }
                suppressLoadErrorMessage = false
                pendingLoadFailureAction = null
                _errorMessage.value = null
            }
            setOnCompletionListener {
                handlePlaybackCompleted()
            }
            setOnErrorListener { _, what, extra ->
                Log.e(tag, "MediaPlayer Error: what=$what extra=$extra")
                _isPlaying.value = false
                val suppressError = suppressLoadErrorMessage
                val onFailure = pendingLoadFailureAction
                suppressLoadErrorMessage = false
                pendingLoadFailureAction = null
                if (suppressError) {
                    resetLoadedSongState()
                    _errorMessage.value = null
                    onFailure?.invoke()
                } else {
                    _errorMessage.value = "无法播放此音乐格式或云端地址不可达"
                }
                stopProgressTracker()
                // Auto skip to next item after 2 seconds on error
                if (!suppressError) {
                    scope.launch {
                        delay(2000)
                        playNext()
                    }
                }
                true
            }
        }
    }

    private fun releasePlayer() {
        stopProgressTracker()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                     it.stop()
                }
            } catch (e: Exception) {
                 Log.e(tag, "Error stopping media player", e)
            }
            it.release()
        }
        mediaPlayer = null
    }

    fun setPlaylist(newPlaylist: List<SongEntity>) {
        _playlist.value = newPlaylist
        Log.d(tag, "Playlist updated with ${newPlaylist.size} tracks")
    }

    fun playSong(song: SongEntity) {
        loadSong(song, startPositionMs = 0L, autoPlay = true, suppressErrorMessage = false, onLoadFailure = null)
        PlaybackService.ensureStarted(context)
    }

    fun restoreSong(song: SongEntity, startPositionMs: Long, onRestoreFailure: (() -> Unit)? = null) {
        loadSong(
            song = song,
            startPositionMs = startPositionMs,
            autoPlay = false,
            suppressErrorMessage = true,
            onLoadFailure = onRestoreFailure
        )
        PlaybackService.ensureStarted(context)
    }

    private fun loadSong(
        song: SongEntity,
        startPositionMs: Long,
        autoPlay: Boolean,
        suppressErrorMessage: Boolean,
        onLoadFailure: (() -> Unit)?
    ) {
        _currentSong.value = song
        _currentPosition.value = startPositionMs.coerceAtLeast(0L)
        _duration.value = 0L
        _isPlaying.value = false
        _errorMessage.value = null
        pendingSeekPositionMs = startPositionMs
        pendingAutoPlay = autoPlay
        suppressLoadErrorMessage = suppressErrorMessage
        pendingLoadFailureAction = onLoadFailure

        try {
            if (mediaPlayer == null) {
                setupMediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.apply {
                val localPath = song.localPath
                if (!File(localPath).exists()) {
                    throw IllegalStateException("Local file missing for ${song.md5sum}")
                }
                setDataSource(localPath)
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to play song: ${song.title}", e)
            val suppressError = suppressLoadErrorMessage
            val onFailure = pendingLoadFailureAction
            suppressLoadErrorMessage = false
            pendingLoadFailureAction = null
            if (suppressError) {
                resetLoadedSongState()
                _errorMessage.value = null
                onFailure?.invoke()
            } else {
                _errorMessage.value = "当前歌曲未下载到本地，或本地文件不可用"
            }
            _isPlaying.value = false
        }
    }

    fun playOrPause() {
        val player = mediaPlayer ?: return
        val current = currentSong.value
        if (current == null && playlist.value.isNotEmpty()) {
             // Play first track if nothing is loaded right now
             playSong(playlist.value.first())
             return
        }
        
        try {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                stopProgressTracker()
            } else {
                player.start()
                _isPlaying.value = true
                startProgressTracker()
            }
            PlaybackService.ensureStarted(context)
        } catch (e: Exception) {
            Log.e(tag, "Error triggering Play/Pause", e)
            _errorMessage.value = "播放控件响应失败"
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        try {
            player.seekTo(positionMs.toInt())
            _currentPosition.value = positionMs
        } catch (e: Exception) {
            Log.e(tag, "Failed seeking", e)
        }
    }

    fun playNext() {
        val list = playlist.value
        if (list.isEmpty()) return
        
        val current = currentSong.value
        val currentIndex = list.indexOfFirst { it.md5sum == current?.md5sum }
        val nextIndex = if (currentIndex != -1) {
            (currentIndex + 1) % list.size
        } else {
            0
        }
        playSong(list[nextIndex])
    }

    fun playPrevious() {
        val list = playlist.value
        if (list.isEmpty()) return

        val current = currentSong.value
        val currentIndex = list.indexOfFirst { it.md5sum == current?.md5sum }
        val prevIndex = if (currentIndex != -1) {
            if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
        } else {
            0
        }
        playSong(list[prevIndex])
    }

    fun toggleLoopMode() {
        _loopMode.value = if (_loopMode.value == LoopMode.LIST) LoopMode.SINGLE else LoopMode.LIST
        Log.d(tag, "Loop mode changed to ${_loopMode.value}")
    }

    fun setLoopMode(loopMode: LoopMode) {
        _loopMode.value = loopMode
    }

    private fun handlePlaybackCompleted() {
        when (loopMode.value) {
            LoopMode.SINGLE -> {
                // Return to begin and loop playing same song
                val current = currentSong.value
                if (current != null) {
                    playSong(current)
                } else {
                    playNext()
                }
            }
            LoopMode.LIST -> {
                playNext()
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition.toLong()
                    }
                }
                delay(400)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun resetLoadedSongState() {
        _currentSong.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
        _isPlaying.value = false
    }

    fun clear() {
        releasePlayer()
        scope.cancel()
    }

    fun snapshotPlayback(): PlaybackSnapshot {
        val livePosition = try {
            mediaPlayer?.currentPosition?.toLong() ?: _currentPosition.value
        } catch (_: Exception) {
            _currentPosition.value
        }
        return PlaybackSnapshot(
            song = _currentSong.value,
            positionMs = livePosition.coerceAtLeast(0L),
            isPlaying = _isPlaying.value
        )
    }
}
