package com.hastur.hmusic.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiPreferencesDataStore by preferencesDataStore(name = "ui_preferences")

class UiPreferencesStore(context: Context) {
    private val appContext = context.applicationContext

    val useIconBottomNavigation: Flow<Boolean> = appContext.uiPreferencesDataStore.data
        .map { preferences -> preferences[Keys.useIconBottomNavigation] ?: false }

    val showStatusBar: Flow<Boolean> = appContext.uiPreferencesDataStore.data
        .map { preferences -> preferences[Keys.showStatusBar] ?: true }

    suspend fun setUseIconBottomNavigation(enabled: Boolean) {
        appContext.uiPreferencesDataStore.edit { preferences ->
            preferences[Keys.useIconBottomNavigation] = enabled
        }
    }

    suspend fun setShowStatusBar(visible: Boolean) {
        appContext.uiPreferencesDataStore.edit { preferences ->
            preferences[Keys.showStatusBar] = visible
        }
    }

    private object Keys {
        val useIconBottomNavigation = booleanPreferencesKey("use_icon_bottom_navigation")
        val showStatusBar = booleanPreferencesKey("show_status_bar")
    }
}
