package com.hastur.hmusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "musicdl_pending_downloads")
data class MusicdlPendingDownloadEntity(
    @PrimaryKey val stableKey: String,
    val taskId: String,
    val sessionId: String,
    val itemId: String,
    val remoteName: String,
    val updatedAt: Long = System.currentTimeMillis()
)
