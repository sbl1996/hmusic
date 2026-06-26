package com.example.sync

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.ResponseBytes
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
import java.net.URI
import java.util.Locale

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

    fun makeFileUrl(key: String): String {
        val cleanKey = key.removePrefix("/")
        val endpointUri = URI.create(normalizedEndpoint)
        val authority = endpointUri.authority ?: normalizedEndpoint.removePrefix("https://").removePrefix("http://")
        return if (forcePathStyle) {
            "${endpointUri.scheme}://$authority/$bucket/$cleanKey"
        } else {
            "${endpointUri.scheme}://$bucket.$authority/$cleanKey"
        }
    }

    suspend fun listMusicFiles(): List<String> {
        val request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .apply {
                if (normalizedPrefix.isNotEmpty()) {
                    prefix(normalizedPrefix)
                }
            }
            .build()

        return s3Client.listObjectsV2Paginator(request)
            .contents()
            .map { it.key() }
            .filter(::isSupportedAudioFile)
            .toList()
    }

    suspend fun uploadPlaylist(playlistJson: String): Boolean {
        val key = playlistKey()
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("application/json; charset=utf-8")
            .build()

        s3Client.putObject(request, RequestBody.fromString(playlistJson))
        return true
    }

    suspend fun downloadPlaylist(): String? {
        val request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(playlistKey())
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

    override fun close() {
        if (s3ClientDelegate.isInitialized()) {
            s3ClientDelegate.value.close()
        }
    }

    private fun playlistKey(): String {
        return if (normalizedPrefix.isEmpty()) {
            PLAYLIST_FILE_NAME
        } else {
            "$normalizedPrefix/$PLAYLIST_FILE_NAME"
        }
    }

    companion object {
        private const val DEFAULT_REGION = "us-east-1"
        private const val PLAYLIST_FILE_NAME = "music_playlist_sync.json"
        private val audioExtensions = setOf(".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg", ".wma")

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

        private fun isSupportedAudioFile(key: String): Boolean {
            val lowerKey = key.lowercase(Locale.ROOT)
            return audioExtensions.any(lowerKey::endsWith)
        }
    }
}
