package com.hastur.hmusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.hastur.hmusic.MainActivity
import com.hastur.hmusic.R
import com.hastur.hmusic.data.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaybackService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var mediaSession: MediaSession
    private var currentSong: SongEntity? = null
    private var isPlaying: Boolean = false
    private var currentPositionMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        playerManager = PlayerManagerProvider.get(applicationContext)
        createNotificationChannel()
        mediaSession = MediaSession(this, "HMusicSession").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        playerManager.playOrPause()
                    }

                    override fun onPause() {
                        playerManager.playOrPause()
                    }

                    override fun onSkipToNext() {
                        playerManager.playNext()
                    }

                    override fun onSkipToPrevious() {
                        playerManager.playPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        playerManager.seekTo(pos)
                    }
                }
            )
            isActive = true
        }

        serviceScope.launch {
            playerManager.currentSong.collectLatest { song ->
                currentSong = song
                updateSessionMetadata(song)
                updateForegroundState()
            }
        }
        serviceScope.launch {
            playerManager.isPlaying.collectLatest { playing ->
                isPlaying = playing
                updatePlaybackState()
                updateForegroundState()
            }
        }
        serviceScope.launch {
            while (true) {
                currentPositionMs = playerManager.currentPosition.value
                updatePlaybackState()
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playerManager.playOrPause()
            ACTION_NEXT -> playerManager.playNext()
            ACTION_PREVIOUS -> playerManager.playPrevious()
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        updateForegroundState()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateForegroundState() {
        val song = currentSong
        if (song == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val notification = buildNotification(song, isPlaying)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(song: SongEntity, playing: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_previous),
            getString(R.string.notification_previous),
            servicePendingIntent(ACTION_PREVIOUS, 2)
        ).build()

        val playPauseAction = Notification.Action.Builder(
            Icon.createWithResource(
                this,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            ),
            getString(if (playing) R.string.notification_pause else R.string.notification_play),
            servicePendingIntent(ACTION_PLAY_PAUSE, 3)
        ).build()

        val nextAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_next),
            getString(R.string.notification_next),
            servicePendingIntent(ACTION_NEXT, 4)
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .setLargeIcon(extractArtwork(song.localPath))
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun updatePlaybackState() {
        val song = currentSong
        val state = when {
            song == null -> PlaybackState.STATE_STOPPED
            isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, currentPositionMs, if (isPlaying) 1f else 0f)
                .build()
        )
    }

    private fun updateSessionMetadata(song: SongEntity?) {
        if (song == null) {
            mediaSession.setMetadata(null)
            return
        }

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, song.durationMs)

        extractArtwork(song.localPath)?.let { metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun extractArtwork(localPath: String): Bitmap? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(localPath)
                val art = retriever.embeddedPicture ?: return@runCatching null
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "hmusic_playback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_PLAY_PAUSE = "com.hastur.hmusic.action.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.hastur.hmusic.action.NEXT"
        private const val ACTION_PREVIOUS = "com.hastur.hmusic.action.PREVIOUS"
        private const val ACTION_STOP = "com.hastur.hmusic.action.STOP"

        fun ensureStarted(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
