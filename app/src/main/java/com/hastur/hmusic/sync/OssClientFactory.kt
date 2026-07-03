package com.hastur.hmusic.sync

import com.hastur.hmusic.data.BackupProfileEntity

class OssClientFactory {
    fun create(profile: BackupProfileEntity?): OssClient? {
        if (profile == null || profile.endpoint.isEmpty()) {
            return null
        }
        return OssClient(
            endpoint = profile.endpoint,
            region = profile.region.ifBlank { OssClient.defaultRegion(profile.endpoint) },
            forcePathStyle = profile.forcePathStyle,
            bucket = profile.bucket,
            accessKeyId = profile.accessKeyId,
            accessKeySecret = profile.accessKeySecret,
            prefix = profile.prefix
        )
    }
}
