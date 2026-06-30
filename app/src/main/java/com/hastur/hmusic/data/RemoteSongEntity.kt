package com.hastur.hmusic.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "remote_songs",
    indices = [Index(value = ["md5sum"], unique = true)]
)
data class RemoteSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val md5sum: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    val fileName: String = "",
    val mimeType: String = "",
    val remoteKey: String,
    val manifestUpdatedAt: Long = 0,
    val syncTime: Long = System.currentTimeMillis()
)
