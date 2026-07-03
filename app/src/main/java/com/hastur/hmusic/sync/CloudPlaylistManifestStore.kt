package com.hastur.hmusic.sync

import com.hastur.hmusic.data.RemoteSongEntity
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class CloudPlaylistManifestStore {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val manifestAdapter: JsonAdapter<CloudPlaylistManifest> =
        moshi.adapter(CloudPlaylistManifest::class.java)

    suspend fun load(client: OssClient): CloudPlaylistManifest? {
        val json = client.downloadManifest() ?: return null
        return manifestAdapter.fromJson(json)
    }

    suspend fun save(client: OssClient, songs: List<RemoteSongEntity>, updatedAt: Long) {
        val manifestJson = manifestAdapter.toJson(
            CloudPlaylistManifest(
                version = 1,
                updatedAt = updatedAt,
                songs = songs.map { CloudSong.fromRemoteSong(it) }
            )
        )
        val success = client.uploadManifest(manifestJson)
        if (!success) {
            error("云端清单写入失败")
        }
    }
}
