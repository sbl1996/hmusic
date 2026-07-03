package com.hastur.hmusic.sync

import com.hastur.hmusic.data.RemoteSongEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CloudPlaylistManifest(
    val version: Int = 1,
    val updatedAt: Long = 0,
    val songs: List<CloudSong> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CloudSong(
    val md5sum: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    val fileName: String = "",
    val mimeType: String = "",
    val remoteKey: String = "",
    val updatedAt: Long = 0
) {
    fun toRemoteSongEntity(profileId: Long): RemoteSongEntity {
        return RemoteSongEntity(
            profileId = profileId,
            md5sum = md5sum,
            title = title.ifBlank { fileName.substringBeforeLast(".") },
            artist = artist.ifBlank { "云端备份" },
            durationMs = durationMs,
            fileName = fileName,
            mimeType = mimeType,
            remoteKey = remoteKey,
            manifestUpdatedAt = updatedAt,
            syncTime = System.currentTimeMillis()
        )
    }

    companion object {
        fun fromRemoteSong(song: RemoteSongEntity): CloudSong {
            return CloudSong(
                md5sum = song.md5sum,
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                fileName = song.fileName,
                mimeType = song.mimeType,
                remoteKey = song.remoteKey,
                updatedAt = song.manifestUpdatedAt
            )
        }
    }
}
