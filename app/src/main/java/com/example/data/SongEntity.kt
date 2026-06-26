package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val url: String,
    val durationMs: Long = 0,
    val isLocal: Boolean = true,
    val fileName: String = "",
    val syncTime: Long = System.currentTimeMillis()
)
