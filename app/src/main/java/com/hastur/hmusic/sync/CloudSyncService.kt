package com.hastur.hmusic.sync

import com.hastur.hmusic.data.BackupProfileEntity
import com.hastur.hmusic.data.MusicRepository
import com.hastur.hmusic.data.RemoteSongEntity
import com.hastur.hmusic.data.SongEntity

data class DownloadSongResult(
    val songTitle: String
)

data class UploadSongResult(
    val songTitle: String
)

data class SyncFromCloudResult(
    val remoteSongCount: Int
)

class CloudSyncService(
    private val repository: MusicRepository,
    private val manifestStore: CloudPlaylistManifestStore,
    private val remoteSongSyncAssembler: RemoteSongSyncAssembler,
    private val songStorage: SongStorage
) {
    suspend fun downloadSong(
        song: RemoteSongEntity,
        existingLocalSong: SongEntity?,
        client: OssClient,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): DownloadSongResult {
        val remoteStream = client.downloadSongStream(song.remoteKey)
        val stored = remoteStream.inputStream.use {
            songStorage.storeRemoteStream(
                remoteKey = song.remoteKey,
                inputStream = it,
                title = song.title,
                artist = song.artist,
                totalBytes = remoteStream.contentLength,
                onProgress = onProgress
            )
        }
        if (stored.md5sum != song.md5sum) {
            songStorage.delete(stored.localPath)
            error("下载后的文件校验失败")
        }

        val localSong = SongEntity(
            id = existingLocalSong?.id ?: 0,
            md5sum = song.md5sum,
            title = existingLocalSong?.title ?: song.title,
            artist = existingLocalSong?.artist ?: song.artist,
            durationMs = if (song.durationMs > 0) song.durationMs else existingLocalSong?.durationMs ?: 0,
            fileName = stored.fileName,
            mimeType = song.mimeType.ifBlank { stored.mimeType },
            localPath = stored.localPath,
            localUpdatedAt = stored.updatedAt,
            syncTime = System.currentTimeMillis()
        )
        repository.insertLocalSong(localSong)
        return DownloadSongResult(songTitle = localSong.title)
    }

    suspend fun uploadSong(
        profile: BackupProfileEntity,
        client: OssClient,
        localSong: SongEntity,
        currentRemoteSongs: List<RemoteSongEntity>
    ): UploadSongResult {
        if (!songStorage.exists(localSong.localPath)) {
            error("本地文件不存在")
        }

        val now = System.currentTimeMillis()
        val remoteSong = remoteSongSyncAssembler.buildRemoteSongEntity(profile.id, localSong, client, now)
        songStorage.openInputStream(localSong.localPath).use { input ->
            client.uploadSongStream(
                remoteKey = remoteSong.remoteKey,
                inputStream = input,
                contentLength = songStorage.size(localSong.localPath) ?: error("无法获取本地文件大小"),
                mimeType = localSong.mimeType
            )
        }

        val mergedRemoteSongs = remoteSongSyncAssembler.mergeRemoteSongs(
            profileId = profile.id,
            incomingSong = remoteSong,
            currentRemoteSongs = currentRemoteSongs,
            manifest = manifestStore.load(client)
        )

        manifestStore.save(client, mergedRemoteSongs, now)
        repository.replaceRemoteSongs(profile.id, mergedRemoteSongs)
        touchProfileLastSync(profile, now)
        return UploadSongResult(songTitle = localSong.title)
    }

    suspend fun syncFromCloud(
        profile: BackupProfileEntity,
        client: OssClient
    ): SyncFromCloudResult {
        val manifest = manifestStore.load(client)
        val remoteEntities = remoteSongSyncAssembler.fromManifest(profile.id, manifest)
        repository.replaceRemoteSongs(profile.id, remoteEntities)
        touchProfileLastSync(profile)
        return SyncFromCloudResult(remoteSongCount = remoteEntities.size)
    }

    suspend fun removeSongFromCloudPlaylist(
        profile: BackupProfileEntity,
        client: OssClient,
        md5sum: String
    ) {
        val now = System.currentTimeMillis()
        val latestManifest = manifestStore.load(client)
        val remainingSongs = remoteSongSyncAssembler
            .fromManifest(profile.id, latestManifest)
            .filterNot { it.md5sum == md5sum }

        manifestStore.save(client, remainingSongs, now)
        repository.replaceRemoteSongs(profile.id, remainingSongs)
        touchProfileLastSync(profile, now)
    }

    private suspend fun touchProfileLastSync(
        profile: BackupProfileEntity,
        syncedAt: Long = System.currentTimeMillis()
    ) {
        repository.updateBackupProfile(
            profile.copy(
                lastSyncAt = syncedAt,
                updatedAt = syncedAt,
                isActive = true
            )
        )
    }
}
