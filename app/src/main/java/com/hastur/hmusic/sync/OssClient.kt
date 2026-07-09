package com.hastur.hmusic.sync

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.Locale

data class RemoteSongStream(
    val inputStream: InputStream,
    val contentLength: Long?
)

class OssClient(
    private val endpoint: String,
    private val region: String,
    private val forcePathStyle: Boolean,
    private val bucket: String,
    private val accessKeyId: String,
    private val accessKeySecret: String,
    private val prefix: String
) : AutoCloseable {
    private val normalizedEndpoint = normalizeEndpoint(endpoint)
    private val normalizedPrefix = normalizePrefix(prefix)

    private val s3ClientDelegate = lazy {
        val builder = S3Client.builder()
            .endpointOverride(URI.create(normalizedEndpoint))
            .region(Region.of(region.ifBlank { DEFAULT_REGION }))
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(forcePathStyle)
                    // OSS rejects aws-chunked payloads. Keeping this off is also safe for other S3-compatible providers.
                    .chunkedEncodingEnabled(false)
                    .build()
            )

        if (accessKeyId.isNotBlank() && accessKeySecret.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, accessKeySecret)
                )
            )
        } else {
            builder.credentialsProvider(AnonymousCredentialsProvider.create())
        }

        builder.build()
    }
    private val s3Client: S3Client
        get() = s3ClientDelegate.value

    suspend fun uploadManifest(playlistJson: String): Boolean {
        val key = manifestKey()
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("application/json; charset=utf-8")
            .build()

        s3Client.putObject(request, RequestBody.fromString(playlistJson))
        return true
    }

    suspend fun downloadManifest(): String? {
        val request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(manifestKey())
            .build()

        val response: ResponseBytes<GetObjectResponse> = try {
            s3Client.getObjectAsBytes(request)
        } catch (_: NoSuchKeyException) {
            return null
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) {
                return null
            }
            throw e
        }

        return response.asUtf8String()
    }

    suspend fun uploadSongFile(remoteKey: String, file: File, mimeType: String): String {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(remoteKey)
            .contentType(mimeType.ifBlank { "application/octet-stream" })
            .build()
        s3Client.putObject(request, RequestBody.fromFile(file))
        return remoteKey
    }

    suspend fun uploadSongStream(
        remoteKey: String,
        inputStream: InputStream,
        contentLength: Long,
        mimeType: String
    ): String {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(remoteKey)
            .contentType(mimeType.ifBlank { "application/octet-stream" })
            .build()
        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength))
        return remoteKey
    }

    suspend fun downloadSongStream(remoteKey: String): RemoteSongStream {
        val request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(remoteKey)
            .build()
        val response: ResponseInputStream<GetObjectResponse> = s3Client.getObject(request)
        val contentLength = response.response().contentLength().takeIf { it > 0 }
        return RemoteSongStream(
            inputStream = response,
            contentLength = contentLength
        )
    }

    override fun close() {
        if (s3ClientDelegate.isInitialized()) {
            s3ClientDelegate.value.close()
        }
    }

    fun buildRemoteAudioKey(md5sum: String, fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        val shardA = md5sum.take(2).ifBlank { "00" }
        val shardB = md5sum.drop(2).take(2).ifBlank { "00" }
        val suffix = if (extension.isBlank()) md5sum else "$md5sum.$extension"
        return buildPrefixedKey("files/$shardA/$shardB/$suffix")
    }

    private fun manifestKey(): String {
        return buildPrefixedKey(PLAYLIST_FILE_NAME)
    }

    private fun buildPrefixedKey(path: String): String {
        return if (normalizedPrefix.isEmpty()) {
            path
        } else {
            "$normalizedPrefix/$path"
        }
    }

    companion object {
        private const val DEFAULT_REGION = "us-east-1"
        private const val PLAYLIST_FILE_NAME = "music_playlist_sync.json"

        fun defaultRegion(endpoint: String): String {
            val clean = endpoint.lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
                .trimEnd('/')

            Regex("s3\\.([a-z0-9-]+)\\.amazonaws\\.com").find(clean)?.let {
                return it.groupValues[1]
            }
            Regex("s3[.-]([a-z0-9-]+)").find(clean)?.let {
                val candidate = it.groupValues[1]
                if (candidate != "endpoint" && candidate != "bucket") {
                    return candidate
                }
            }
            Regex("oss[.-]([a-z0-9-]+)").find(clean)?.let {
                return it.groupValues[1]
            }
            Regex("cos\\.([a-z0-9-]+)\\.myqcloud\\.com").find(clean)?.let {
                return it.groupValues[1]
            }
            return DEFAULT_REGION
        }

        fun defaultForcePathStyle(endpoint: String): Boolean {
            val clean = endpoint.lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
                .trimEnd('/')

            if (clean.contains("aliyuncs.com")) return false
            if (clean.contains("qiniucs.com")) return true
            if (clean.contains("localhost") || clean.contains(":")) return true

            val hostParts = clean.substringBefore('/').split(".")
            if (hostParts.all { it.all(Char::isDigit) }) return true
            if (hostParts.size > 3 && !clean.contains("amazonaws.com")) return true

            return false
        }

        private fun normalizeEndpoint(endpoint: String): String {
            val trimmed = endpoint.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }

        private fun normalizePrefix(prefix: String): String {
            return prefix.trim().trim('/').removePrefix("/")
        }
    }
}
