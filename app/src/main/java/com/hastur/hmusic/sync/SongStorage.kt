package com.hastur.hmusic.sync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

data class StoredSongFile(
    val md5sum: String,
    val file: File,
    val fileName: String,
    val mimeType: String,
    val updatedAt: Long
)

class SongStorage(private val context: Context) {
    private val songDir = File(context.filesDir, "songs").apply { mkdirs() }

    fun importFromUri(uriString: String): StoredSongFile {
        val uri = Uri.parse(uriString)
        val contentResolver = context.contentResolver
        val originalName = queryDisplayName(contentResolver, uri) ?: uri.lastPathSegment ?: "track"
        val normalizedName = normalizeFileName(originalName)
        val mimeType = contentResolver.getType(uri).orEmpty()
        val tempFile = File(songDir, "import-${System.currentTimeMillis()}-$normalizedName")

        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("无法读取所选音频文件")

        val md5 = fileMd5(tempFile)
        val finalFile = File(songDir, buildStorageName(md5, normalizedName))
        if (finalFile.absolutePath != tempFile.absolutePath) {
            if (finalFile.exists()) {
                tempFile.delete()
            } else if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
        }

        return StoredSongFile(
            md5sum = md5,
            file = finalFile,
            fileName = normalizedName,
            mimeType = mimeType,
            updatedAt = finalFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    }

    fun storeRemoteStream(
        remoteKey: String,
        inputStream: InputStream,
        totalBytes: Long? = null,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): StoredSongFile {
        val normalizedName = normalizeFileName(remoteKey.substringAfterLast("/").ifBlank { "track" })
        val tempFile = File(songDir, "download-${System.currentTimeMillis()}-$normalizedName")
        tempFile.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead = 0L
            while (true) {
                val read = inputStream.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                bytesRead += read
                onProgress(bytesRead, totalBytes)
            }
        }

        val md5 = fileMd5(tempFile)
        val finalFile = File(songDir, buildStorageName(md5, normalizedName))
        if (finalFile.absolutePath != tempFile.absolutePath) {
            if (finalFile.exists()) {
                tempFile.delete()
            } else if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
        }

        return StoredSongFile(
            md5sum = md5,
            file = finalFile,
            fileName = normalizedName,
            mimeType = mimeTypeFromName(normalizedName),
            updatedAt = finalFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    }

    fun fileForPath(path: String?): File? {
        if (path.isNullOrBlank()) return null
        return File(path)
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
        return null
    }

    private fun normalizeFileName(name: String): String {
        val trimmed = name.trim().ifBlank { "track" }
        return trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
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
