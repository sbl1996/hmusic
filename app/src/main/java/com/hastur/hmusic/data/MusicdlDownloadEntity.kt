package com.hastur.hmusic.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "musicdl_downloads",
    indices = [
        Index(value = ["stableKey"], unique = true),
        Index(value = ["md5sum"])
    ]
)
data class MusicdlDownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableKey: String,
    val md5sum: String,
    val source: String = "",
    val songName: String = "",
    val singers: String = "",
    val fileSizeBytes: Long? = null,
    val fileSize: String = "",
    val extension: String = "",
    val durationSeconds: Long? = null,
    val duration: String = "",
    val identifier: String = "",
    val album: String = "",
    val coverUrl: String = "",
    val downloadedAt: Long = System.currentTimeMillis()
)
