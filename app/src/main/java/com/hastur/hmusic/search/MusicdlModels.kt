package com.hastur.hmusic.search

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MusicdlHealthResponse(
    val status: String,
    val downloadRoot: String,
    val sessionTtlSeconds: Int,
    val defaultSources: List<String>
)

@JsonClass(generateAdapter = true)
data class MusicdlSearchRequest(
    val keyword: String,
    val sources: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class MusicdlSearchResponse(
    val sessionId: String,
    val keyword: String,
    val sources: List<String>,
    val createdAt: String,
    val expiresAt: String,
    val itemCount: Int,
    val items: List<MusicdlSearchItem>
)

@JsonClass(generateAdapter = true)
data class MusicdlSearchTaskResponse(
    val searchId: String,
    val status: String,
    val keyword: String,
    val sources: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val error: String? = null,
    val result: MusicdlSearchResponse? = null
) {
    val isTerminal: Boolean
        get() = status == "completed" || status == "failed"
}

@JsonClass(generateAdapter = true)
data class MusicdlSearchItem(
    val itemId: String,
    val songName: String? = null,
    val singers: String? = null,
    val album: String? = null,
    val source: String? = null,
    val rootSource: String? = null,
    val fileSize: String? = null,
    val fileSizeBytes: Long? = null,
    val duration: String? = null,
    val durationSeconds: Long? = null,
    val extension: String? = null,
    val identifier: String? = null,
    val downloadProtocol: String? = null,
    val coverUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class MusicdlDownloadRequest(
    val sessionId: String,
    val itemId: String
)

@JsonClass(generateAdapter = true)
data class MusicdlDownloadTaskResponse(
    val taskId: String,
    val status: String,
    val sessionId: String,
    val itemId: String,
    val createdAt: String,
    val updatedAt: String,
    val error: String? = null,
    val progress: MusicdlDownloadProgress,
    val result: MusicdlDownloadResult? = null
) {
    val isTerminal: Boolean
        get() = status == "completed" || status == "failed"
}

@JsonClass(generateAdapter = true)
data class MusicdlDownloadProgress(
    val savePath: String? = null,
    val fileExists: Boolean,
    val downloadedBytes: Long,
    val totalBytes: Long? = null,
    val percent: Double? = null
)

@JsonClass(generateAdapter = true)
data class MusicdlDownloadResult(
    val songName: String? = null,
    val singers: String? = null,
    val album: String? = null,
    val source: String? = null,
    val extension: String? = null,
    val savePath: String? = null,
    val fileSize: String? = null,
    val duration: String? = null
)

@JsonClass(generateAdapter = true)
data class MusicdlDownloadStorageResponse(
    val usedBytes: Long,
    val fileCount: Int
)

@JsonClass(generateAdapter = true)
data class MusicdlDownloadCleanupResponse(
    val deletedBytes: Long,
    val deletedFileCount: Int,
    val deletedTaskCount: Int,
    val skippedActiveTaskCount: Int
)

@JsonClass(generateAdapter = true)
data class MusicdlErrorResponse(
    val detail: Any? = null
)
