package com.hastur.hmusic.sync

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

data class StoredSongFile(
    val md5sum: String,
    val localPath: String,
    val fileName: String,
    val mimeType: String,
    val updatedAt: Long
)

data class ScannedSongFile(
    val md5sum: String,
    val localPath: String,
    val fileName: String,
    val mimeType: String,
    val updatedAt: Long,
    val embeddedTitle: String?,
    val embeddedArtist: String?,
    val durationMs: Long
)

class SongStorage(private val context: Context) {
    private val partialDir = File(context.cacheDir, "song-downloads").apply { mkdirs() }
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasConfiguredDirectory(): Boolean = configuredDirectoryUri() != null

    fun configureDirectory(uri: Uri) {
        val documentId = DocumentsContract.getTreeDocumentId(uri).trimEnd('/')
        require(documentId.equals("primary:Download/hmusic", ignoreCase = true)) {
            "请选择 Download/hmusic 目录"
        }
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        preferences.edit().putString(KEY_DIRECTORY_URI, uri.toString()).apply()
    }

    fun importFromUri(uriString: String, title: String, artist: String): StoredSongFile {
        val uri = Uri.parse(uriString)
        val contentResolver = context.contentResolver
        val originalName = queryDisplayName(contentResolver, uri) ?: uri.lastPathSegment ?: "track"
        val normalizedName = normalizeFileName(originalName)
        val mimeType = contentResolver.getType(uri).orEmpty().ifBlank { mimeTypeFromName(normalizedName) }
        val tempFile = File(partialDir, "import-${System.currentTimeMillis()}-$normalizedName")

        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use(input::copyTo)
        } ?: error("无法读取所选音频文件")

        return finalizeTempFile(tempFile, normalizedName, mimeType, title, artist)
    }

    fun storeRemoteStream(
        remoteKey: String,
        inputStream: InputStream,
        title: String,
        artist: String,
        totalBytes: Long? = null,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): StoredSongFile {
        val normalizedName = normalizeFileName(remoteKey.substringAfterLast("/").ifBlank { "track" })
        val tempFile = File(partialDir, "download-${System.currentTimeMillis()}-$normalizedName")
        writeStream(tempFile, inputStream, false, 0L, totalBytes, onProgress)
        return finalizeTempFile(
            tempFile = tempFile,
            originalName = normalizedName,
            mimeType = mimeTypeFromName(normalizedName),
            title = title,
            artist = artist
        )
    }

    fun partialRemoteBytes(remoteKey: String): Long {
        return partialRemoteFile(remoteKey).length().takeIf { it > 0L } ?: 0L
    }

    fun storeRemoteStreamResumable(
        remoteKey: String,
        inputStream: InputStream,
        title: String,
        artist: String,
        totalBytes: Long? = null,
        expectedBytes: Long? = null,
        append: Boolean = false,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): StoredSongFile {
        val normalizedName = normalizeFileName(remoteKey.substringAfterLast("/").ifBlank { "track" })
        val partialFile = partialRemoteFile(remoteKey)
        val bytesRead = writeStream(
            partialFile,
            inputStream,
            append,
            if (append) partialFile.length() else 0L,
            totalBytes,
            onProgress
        )
        if (expectedBytes != null && bytesRead != expectedBytes) {
            error("文件下载不完整：已接收 $bytesRead 字节，应为 $expectedBytes 字节")
        }
        return finalizeTempFile(
            tempFile = partialFile,
            originalName = normalizedName,
            mimeType = mimeTypeFromName(normalizedName),
            title = title,
            artist = artist
        )
    }

    fun exists(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        val uri = Uri.parse(localPath)
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
        } else {
            File(localPath).exists()
        }
    }

    fun size(localPath: String?): Long? {
        if (localPath.isNullOrBlank()) return null
        val uri = Uri.parse(localPath)
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        } else {
            File(localPath).takeIf(File::exists)?.length()
        }
    }

    fun displayName(localPath: String?): String? {
        if (localPath.isNullOrBlank()) return null
        val uri = Uri.parse(localPath)
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            queryDisplayName(context.contentResolver, uri)
        } else {
            File(localPath).name
        }
    }

    fun displayPath(localPath: String?): String? {
        if (localPath.isNullOrBlank()) return null
        val uri = Uri.parse(localPath)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return File(localPath).absolutePath
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return localPath
        return if (documentId.startsWith("primary:")) {
            "/storage/emulated/0/${documentId.removePrefix("primary:")}"
        } else {
            localPath
        }
    }

    fun openInputStream(localPath: String): InputStream {
        val uri = Uri.parse(localPath)
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.openInputStream(uri) ?: error("无法读取本地文件")
        } else {
            FileInputStream(localPath)
        }
    }

    fun delete(localPath: String): Boolean {
        val uri = Uri.parse(localPath)
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }.getOrDefault(false)
        } else {
            val file = File(localPath)
            !file.exists() || file.delete()
        }
    }

    fun durationMs(localPath: String): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(localPath)
                if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    retriever.setDataSource(context, uri)
                } else {
                    retriever.setDataSource(localPath)
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(0L)
    }

    fun scanConfiguredDirectory(
        knownLocalPaths: Set<String>,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ScannedSongFile> {
        val parentUri = configuredDirectoryUri() ?: error("请先选择 Download/hmusic 存储目录")
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getTreeDocumentId(parentUri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val files = mutableListOf<ScannedSongFile>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val total = cursor.count
            var scanned = 0
            while (cursor.moveToNext()) {
                scanned += 1
                val documentId = cursor.getString(0)
                val displayName = cursor.getString(1).orEmpty()
                val reportedMimeType = cursor.getString(2).orEmpty()
                if (!isAudioFile(displayName, reportedMimeType)) {
                    onProgress(scanned, total)
                    continue
                }

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId)
                val localPath = documentUri.toString()
                if (localPath in knownLocalPaths) {
                    onProgress(scanned, total)
                    continue
                }
                val metadata = readAudioMetadata(localPath)
                files += ScannedSongFile(
                    md5sum = context.contentResolver.openInputStream(documentUri)?.use(::streamMd5)
                        ?: error("无法读取本地文件：$displayName"),
                    localPath = localPath,
                    fileName = displayName,
                    mimeType = reportedMimeType.ifBlank { mimeTypeFromName(displayName) },
                    updatedAt = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                    embeddedTitle = metadata.title,
                    embeddedArtist = metadata.artist,
                    durationMs = metadata.durationMs
                )
                onProgress(scanned, total)
            }
        }
        return files
    }

    private fun finalizeTempFile(
        tempFile: File,
        originalName: String,
        mimeType: String,
        title: String,
        artist: String
    ): StoredSongFile {
        try {
            val md5 = fileMd5(tempFile)
            val preferredName = buildStorageName(artist, title, originalName)
            val (storageName, existingUri) = resolveStorageTarget(preferredName, md5)
            val targetUri = existingUri
                ?: DocumentsContract.createDocument(
                    context.contentResolver,
                    configuredDirectoryDocumentUri(),
                    mimeType.ifBlank { "application/octet-stream" },
                    storageName
                )
                ?: error("无法在所选目录创建文件")

            context.contentResolver.openOutputStream(targetUri, "wt")?.use { output ->
                tempFile.inputStream().use { it.copyTo(output) }
            } ?: error("无法写入所选目录")

            return StoredSongFile(
                md5sum = md5,
                localPath = targetUri.toString(),
                fileName = storageName,
                mimeType = mimeType,
                updatedAt = System.currentTimeMillis()
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun resolveStorageTarget(preferredName: String, md5: String): Pair<String, Uri?> {
        var sequence = 1
        while (true) {
            val candidateName = if (sequence == 1) preferredName else appendSequence(preferredName, sequence)
            val existing = findDocument(candidateName)
            if (existing == null) return candidateName to null
            val existingMd5 = context.contentResolver.openInputStream(existing)?.use(::streamMd5)
            if (existingMd5 == md5) return candidateName to existing
            sequence += 1
        }
    }

    private fun findDocument(displayName: String): Uri? {
        val parentUri = configuredDirectoryUri() ?: error("请先选择 Download/hmusic 存储目录")
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getTreeDocumentId(parentUri)
        )
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == displayName) {
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, cursor.getString(0))
                }
            }
        }
        return null
    }

    private fun configuredDirectoryDocumentUri(): Uri {
        val treeUri = configuredDirectoryUri() ?: error("请先选择 Download/hmusic 存储目录")
        return DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
    }

    private fun configuredDirectoryUri(): Uri? {
        val uri = preferences.getString(KEY_DIRECTORY_URI, null)?.let(Uri::parse) ?: return null
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return uri.takeIf { hasPermission }
    }

    private fun writeStream(
        file: File,
        inputStream: InputStream,
        append: Boolean,
        initialBytes: Long,
        totalBytes: Long?,
        onProgress: (Long, Long?) -> Unit
    ): Long {
        var bytesRead = initialBytes
        FileOutputStream(file, append).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = inputStream.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                bytesRead += read
                onProgress(bytesRead, totalBytes)
            }
        }
        return bytesRead
    }

    private fun partialRemoteFile(remoteKey: String): File {
        val normalizedName = normalizeFileName(remoteKey.substringAfterLast("/").ifBlank { "track" })
        val stableName = normalizeFileName(remoteKey).ifBlank { normalizedName }
        return File(partialDir, "download-$stableName.part")
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private fun normalizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trimEnd('.', ' ')
            .ifBlank { "未知" }
            .take(MAX_FILE_NAME_COMPONENT_LENGTH)
    }

    private fun buildStorageName(artist: String, title: String, originalName: String): String {
        val extension = originalName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val readableName = "${normalizeFileName(artist.ifBlank { "佚名" })} - ${
            normalizeFileName(title.ifBlank { "未知歌曲" })
        }"
        return if (extension.isBlank()) readableName else "$readableName.$extension"
    }

    private fun appendSequence(fileName: String, sequence: Int): String {
        val extension = fileName.substringAfterLast('.', "").takeIf { fileName.contains('.') }.orEmpty()
        val baseName = if (extension.isBlank()) fileName else fileName.removeSuffix(".$extension")
        return if (extension.isBlank()) "$baseName ($sequence)" else "$baseName ($sequence).$extension"
    }

    private fun isAudioFile(fileName: String, mimeType: String): Boolean {
        return mimeType.startsWith("audio/") ||
            fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXTENSIONS
    }

    private data class AudioMetadata(
        val title: String?,
        val artist: String?,
        val durationMs: Long
    )

    private fun readAudioMetadata(localPath: String): AudioMetadata {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(localPath))
                AudioMetadata(
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.trim()
                        ?.takeIf(String::isNotBlank),
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.trim()
                        ?.takeIf(String::isNotBlank),
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.coerceAtLeast(0L)
                        ?: 0L
                )
            } finally {
                retriever.release()
            }
        }.getOrElse { AudioMetadata(null, null, 0L) }
    }

    private fun mimeTypeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "audio/*"
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "song_storage"
        private const val KEY_DIRECTORY_URI = "directory_uri"
        private const val MAX_FILE_NAME_COMPONENT_LENGTH = 96
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg")

        fun fileMd5(file: File): String {
            return FileInputStream(file).use(::streamMd5)
        }

        private fun streamMd5(input: InputStream): String {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
