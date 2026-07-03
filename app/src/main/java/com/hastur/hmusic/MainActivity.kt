package com.hastur.hmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.hastur.hmusic.data.MusicDatabase
import com.hastur.hmusic.data.MusicRepository
import com.hastur.hmusic.player.MusicPlayerManager
import com.hastur.hmusic.player.PlayerManagerProvider
import com.hastur.hmusic.player.PlaybackStateStore
import com.hastur.hmusic.sync.CloudPlaylistManifestStore
import com.hastur.hmusic.sync.CloudSyncService
import com.hastur.hmusic.sync.OssClientFactory
import com.hastur.hmusic.sync.RemoteSongSyncAssembler
import com.hastur.hmusic.sync.SongStorage
import com.hastur.hmusic.ui.MusicPlayerScreen
import com.hastur.hmusic.ui.MusicViewModel
import com.hastur.hmusic.ui.MusicViewModelFactory
import com.hastur.hmusic.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var repository: MusicRepository
    private lateinit var songStorage: SongStorage
    private lateinit var playbackStateStore: PlaybackStateStore
    private lateinit var manifestStore: CloudPlaylistManifestStore
    private lateinit var remoteSongSyncAssembler: RemoteSongSyncAssembler
    private lateinit var cloudSyncService: CloudSyncService
    private lateinit var ossClientFactory: OssClientFactory
    private val viewModel: MusicViewModel by viewModels {
        MusicViewModelFactory(
            repository,
            playerManager,
            songStorage,
            playbackStateStore,
            cloudSyncService,
            ossClientFactory
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Core System instances
        val database = MusicDatabase.getDatabase(this)
        repository = MusicRepository(database.musicDao())
        playerManager = PlayerManagerProvider.get(this)
        songStorage = SongStorage(applicationContext)
        playbackStateStore = PlaybackStateStore(applicationContext)
        manifestStore = CloudPlaylistManifestStore()
        remoteSongSyncAssembler = RemoteSongSyncAssembler()
        cloudSyncService = CloudSyncService(repository, manifestStore, remoteSongSyncAssembler, songStorage)
        ossClientFactory = OssClientFactory()

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) { // Default to ultra-premium dark theme
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MusicPlayerScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.persistPlaybackState()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
