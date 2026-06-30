package com.hastur.hmusic.player

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

data class PersistedPlaybackState(
    val currentSongMd5: String,
    val currentPositionMs: Long,
    val isPlaying: Boolean,
    val loopMode: String
)

private val Context.playbackStateDataStore by preferencesDataStore(name = "playback_state")

class PlaybackStateStore(context: Context) {
    private val appContext = context.applicationContext

    suspend fun read(): PersistedPlaybackState? {
        return appContext.playbackStateDataStore.data.first()
            .toPlaybackState()
            .takeIf { it.currentSongMd5.isNotBlank() }
    }

    suspend fun save(currentSongMd5: String, currentPositionMs: Long, isPlaying: Boolean, loopMode: String) {
        appContext.playbackStateDataStore.edit { preferences ->
            preferences[Keys.currentSongMd5] = currentSongMd5
            preferences[Keys.currentPositionMs] = currentPositionMs.coerceAtLeast(0L)
            preferences[Keys.isPlaying] = isPlaying
            preferences[Keys.loopMode] = loopMode
        }
    }

    suspend fun clear() {
        appContext.playbackStateDataStore.edit { preferences ->
            preferences.remove(Keys.currentSongMd5)
            preferences.remove(Keys.currentPositionMs)
            preferences.remove(Keys.isPlaying)
            preferences.remove(Keys.loopMode)
        }
    }

    private fun Preferences.toPlaybackState(): PersistedPlaybackState {
        return PersistedPlaybackState(
            currentSongMd5 = this[Keys.currentSongMd5].orEmpty(),
            currentPositionMs = this[Keys.currentPositionMs] ?: 0L,
            isPlaying = this[Keys.isPlaying] ?: false,
            loopMode = this[Keys.loopMode].orEmpty()
        )
    }

    private object Keys {
        val currentSongMd5 = stringPreferencesKey("current_song_md5")
        val currentPositionMs = longPreferencesKey("current_position_ms")
        val isPlaying = booleanPreferencesKey("is_playing")
        val loopMode = stringPreferencesKey("loop_mode")
    }
}
