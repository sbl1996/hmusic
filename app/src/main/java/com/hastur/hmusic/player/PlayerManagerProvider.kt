package com.hastur.hmusic.player

import android.content.Context

object PlayerManagerProvider {
    @Volatile
    private var instance: MusicPlayerManager? = null

    fun get(context: Context): MusicPlayerManager {
        return instance ?: synchronized(this) {
            instance ?: MusicPlayerManager(context.applicationContext).also { instance = it }
        }
    }
}
