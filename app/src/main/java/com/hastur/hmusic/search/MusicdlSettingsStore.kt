package com.hastur.hmusic.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.musicdlSettingsDataStore by preferencesDataStore(name = "musicdl_settings")

class MusicdlSettingsStore(context: Context) {
    private val appContext = context.applicationContext

    val baseUrlFlow: Flow<String> = appContext.musicdlSettingsDataStore.data
        .map { preferences -> preferences[Keys.baseUrl] ?: DEFAULT_BASE_URL }

    suspend fun saveBaseUrl(baseUrl: String) {
        appContext.musicdlSettingsDataStore.edit { preferences ->
            preferences[Keys.baseUrl] = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
        }
    }

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"
    }
}
