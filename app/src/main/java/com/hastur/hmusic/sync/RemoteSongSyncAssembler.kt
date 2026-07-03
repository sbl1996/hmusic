package com.hastur.hmusic.sync

import com.hastur.hmusic.data.RemoteSongEntity
import com.hastur.hmusic.data.SongEntity
import java.io.File

class RemoteSongSyncAssembler {
    fun fromManifest(profileId: Long, manifest: CloudPlaylistManifest?): List<RemoteSongEntity> {
        return manifest?.songs?.map { it.toRemoteSongEntity(profileId) }.orEmpty()
    }

    fun buildRemoteSongEntity(
        profileId: Long,
        localSong: SongEntity,
        client: OssClient,
        updatedAt: Long
    ): RemoteSongEntity {
        val remoteKey = client.buildRemoteAudioKey(
            localSong.md5sum,
            localSong.fileName.ifBlank { File(localSong.localPath).name }
        )
        return RemoteSongEntity(
            profileId = profileId,
            md5sum = localSong.md5sum,
            title = localSong.title,
            artist = localSong.artist,
            durationMs = localSong.durationMs,
            fileName = localSong.fileName,
            mimeType = localSong.mimeType,
            remoteKey = remoteKey,
            manifestUpdatedAt = maxOf(localSong.localUpdatedAt, updatedAt),
            syncTime = updatedAt
        )
    }

    fun mergeRemoteSongs(
        profileId: Long,
        incomingSong: RemoteSongEntity,
        currentRemoteSongs: List<RemoteSongEntity>,
        manifest: CloudPlaylistManifest?
    ): List<RemoteSongEntity> {
        val mergedByMd5 = linkedMapOf<String, RemoteSongEntity>()
        manifest?.songs
            ?.map { it.toRemoteSongEntity(profileId) }
            ?.forEach { mergedByMd5[it.md5sum] = it }
        currentRemoteSongs.forEach { mergedByMd5[it.md5sum] = it }
        mergedByMd5[incomingSong.md5sum] = incomingSong
        return mergedByMd5.values.toList()
    }
}
