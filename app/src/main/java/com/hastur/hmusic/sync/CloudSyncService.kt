package com.hastur.hmusic.sync

import com.hastur.hmusic.data.BackupProfileEntity
import com.hastur.hmusic.data.MusicRepository
import com.hastur.hmusic.data.RemoteSongEntity
import com.hastur.hmusic.data.SongEntity
import java.io.File

data class DownloadSongResult(
    val songTitle: String
)

data class UploadSongResult(
    val songTitle: String
)

data class SyncFromCloudResult(
    val remoteSongCount: Int
)

data class BackupPlaylistResult(
    val uploadedSongCount: Int
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
                totalBytes = remoteStream.contentLength,
                onProgress = onProgress
            )
        }
        if (stored.md5sum != song.md5sum) {
            stored.file.delete()
            error("下载后的文件校验失败")
        }

        val localSong = SongEntity(
            id = existingLocalSong?.id ?: 0,
            md5sum = song.md5sum,
            title = existingLocalSong?.title ?: song.title,
            artist = existingLocalSong?.artist ?: song.artist,
            durationMs = if (song.durationMs > 0) song.durationMs else existingLocalSong?.durationMs ?: 0,
            fileName = song.fileName.ifBlank { stored.fileName },
            mimeType = song.mimeType.ifBlank { stored.mimeType },
            localPath = stored.file.absolutePath,
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
        val localFile = File(localSong.localPath)
        if (!localFile.exists()) {
            error("本地文件不存在")
        }

        val now = System.currentTimeMillis()
        val remoteSong = remoteSongSyncAssembler.buildRemoteSongEntity(profile.id, localSong, client, now)
        client.uploadSongFile(
            remoteKey = remoteSong.remoteKey,
            file = localFile,
            mimeType = localSong.mimeType
        )

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

    suspend fun backupPlaylist(
        profile: BackupProfileEntity,
        client: OssClient,
        localSongs: List<SongEntity>
    ): BackupPlaylistResult {
        val now = System.currentTimeMillis()
        val syncedRemoteSongs = localSongs
            .filter { it.isDownloaded }
            .map { localSong ->
                val remoteSong = remoteSongSyncAssembler.buildRemoteSongEntity(
                    profile.id,
                    localSong,
                    client,
                    now
                )
                client.uploadSongFile(remoteSong.remoteKey, File(localSong.localPath), localSong.mimeType)
                remoteSong
            }

        manifestStore.save(client, syncedRemoteSongs, now)
        repository.replaceRemoteSongs(profile.id, syncedRemoteSongs)
        touchProfileLastSync(profile, now)
        return BackupPlaylistResult(uploadedSongCount = syncedRemoteSongs.size)
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
