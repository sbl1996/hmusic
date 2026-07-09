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

    fun importFromUri(uriString: String): StoredSongFile {
        val uri = Uri.parse(uriString)
        val contentResolver = context.contentResolver
        val originalName = queryDisplayName(contentResolver, uri) ?: uri.lastPathSegment ?: "track"
        val normalizedName = normalizeFileName(originalName)
        val mimeType = contentResolver.getType(uri).orEmpty().ifBlank { mimeTypeFromName(normalizedName) }
        val tempFile = File(partialDir, "import-${System.currentTimeMillis()}-$normalizedName")

        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use(input::copyTo)
        } ?: error("无法读取所选音频文件")

        return finalizeTempFile(tempFile, normalizedName, mimeType)
    }

    fun storeRemoteStream(
        remoteKey: String,
        inputStream: InputStream,
        totalBytes: Long? = null,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): StoredSongFile {
        val normalizedName = normalizeFileName(remoteKey.substringAfterLast("/").ifBlank { "track" })
        val tempFile = File(partialDir, "download-${System.currentTimeMillis()}-$normalizedName")
        writeStream(tempFile, inputStream, false, 0L, totalBytes, onProgress)
        return finalizeTempFile(tempFile, normalizedName, mimeTypeFromName(normalizedName))
    }

    fun partialRemoteBytes(remoteKey: String): Long {
        return partialRemoteFile(remoteKey).length().takeIf { it > 0L } ?: 0L
    }

    fun storeRemoteStreamResumable(
        remoteKey: String,
        inputStream: InputStream,
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
        return finalizeTempFile(partialFile, normalizedName, mimeTypeFromName(normalizedName))
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

    private fun finalizeTempFile(tempFile: File, originalName: String, mimeType: String): StoredSongFile {
        try {
            val md5 = fileMd5(tempFile)
            val storageName = buildStorageName(md5, originalName)
            val targetUri = findDocument(storageName)
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
                fileName = originalName,
                mimeType = mimeType,
                updatedAt = System.currentTimeMillis()
            )
        } finally {
            tempFile.delete()
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
        return name.trim().ifBlank { "track" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun buildStorageName(md5: String, originalName: String): String {
        val extension = originalName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (extension.isBlank()) md5 else "$md5.$extension"
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

        fun fileMd5(file: File): String {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
