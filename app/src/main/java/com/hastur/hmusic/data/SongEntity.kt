package com.hastur.hmusic.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import android.net.Uri
import java.io.File

@Entity(
    tableName = "songs",
    indices = [Index(value = ["md5sum"], unique = true)]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val md5sum: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    val fileName: String = "",
    val mimeType: String = "",
    val localPath: String,
    val localUpdatedAt: Long = 0,
    val syncTime: Long = System.currentTimeMillis()
) {
    val isDownloaded: Boolean
        get() = localPath.isNotBlank() && (
            Uri.parse(localPath).scheme == "content" || File(localPath).exists()
        )
}
