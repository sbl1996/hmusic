package com.hastur.hmusic.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.clipboardConfigPromptDataStore by preferencesDataStore(name = "clipboard_config_prompt")

class ClipboardConfigPromptStore(context: Context) {
    private val appContext = context.applicationContext

    suspend fun readDismissedConfigHash(): String? {
        return appContext.clipboardConfigPromptDataStore.data.first()[Keys.dismissedConfigHash]
    }

    suspend fun saveDismissedConfigHash(hash: String) {
        appContext.clipboardConfigPromptDataStore.edit { preferences ->
            preferences[Keys.dismissedConfigHash] = hash
        }
    }

    private object Keys {
        val dismissedConfigHash = stringPreferencesKey("dismissed_config_hash")
    }
}
