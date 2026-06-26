package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.player.MusicPlayerManager
import com.example.ui.MusicPlayerScreen
import com.example.ui.MusicViewModel
import com.example.ui.MusicViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var playerManager: MusicPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Core System instances
        val database = MusicDatabase.getDatabase(this)
        val repository = MusicRepository(database.musicDao())
        playerManager = MusicPlayerManager(this)

        val viewModel: MusicViewModel by viewModels {
            MusicViewModelFactory(repository, playerManager)
        }

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

    override fun onDestroy() {
        super.onDestroy()
        if (::playerManager.isInitialized) {
            playerManager.clear()
        }
    }
}
