package com.hastur.hmusic.search

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MusicdlApiException(message: String) : Exception(message)

class MusicdlApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val healthAdapter = moshi.adapter(MusicdlHealthResponse::class.java)
    private val searchRequestAdapter = moshi.adapter(MusicdlSearchRequest::class.java)
    private val searchResponseAdapter = moshi.adapter(MusicdlSearchResponse::class.java)
    private val searchTaskAdapter = moshi.adapter(MusicdlSearchTaskResponse::class.java)
    private val downloadRequestAdapter = moshi.adapter(MusicdlDownloadRequest::class.java)
    private val downloadTaskAdapter = moshi.adapter(MusicdlDownloadTaskResponse::class.java)

    fun health(baseUrl: String): MusicdlHealthResponse {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "health"))
            .get()
            .build()
        return executeJson(request, healthAdapter::fromJson)
    }

    fun search(baseUrl: String, keyword: String, sources: List<String>? = null): MusicdlSearchResponse {
        val body = searchRequestAdapter
            .toJson(MusicdlSearchRequest(keyword = keyword, sources = sources?.takeIf { it.isNotEmpty() }))
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "search"))
            .post(body)
            .build()
        return executeJson(request, searchResponseAdapter::fromJson)
    }

    fun createSearch(baseUrl: String, keyword: String, sources: List<String>? = null): MusicdlSearchTaskResponse {
        val body = searchRequestAdapter
            .toJson(MusicdlSearchRequest(keyword = keyword, sources = sources?.takeIf { it.isNotEmpty() }))
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "searches"))
            .post(body)
            .build()
        return executeJson(request, searchTaskAdapter::fromJson)
    }

    fun getSearch(baseUrl: String, searchId: String): MusicdlSearchTaskResponse {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "searches/$searchId"))
            .get()
            .build()
        return executeJson(request, searchTaskAdapter::fromJson)
    }

    fun createDownload(baseUrl: String, sessionId: String, itemId: String): MusicdlDownloadTaskResponse {
        val body = downloadRequestAdapter
            .toJson(MusicdlDownloadRequest(sessionId = sessionId, itemId = itemId))
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "downloads"))
            .post(body)
            .build()
        return executeJson(request, downloadTaskAdapter::fromJson)
    }

    fun getDownload(baseUrl: String, taskId: String): MusicdlDownloadTaskResponse {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "downloads/$taskId"))
            .get()
            .build()
        return executeJson(request, downloadTaskAdapter::fromJson)
    }

    fun <T> downloadFile(
        baseUrl: String,
        taskId: String,
        disposition: String = "attachment",
        block: (inputStream: InputStream, contentLength: Long?) -> T
    ): T {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "downloads/$taskId/file?disposition=$disposition"))
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MusicdlApiException("musicdl 文件下载失败：HTTP ${response.code} ${response.message}")
            }
            val body = response.body ?: throw MusicdlApiException("musicdl 文件响应为空")
            return body.byteStream().use { input ->
                block(input, body.contentLength().takeIf { it >= 0L })
            }
        }
    }

    private fun <T> executeJson(request: Request, parse: (String) -> T?): T {
        httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw MusicdlApiException("musicdl 请求失败：HTTP ${response.code} ${response.message}${bodyText.errorSuffix()}")
            }
            return parse(bodyText) ?: throw MusicdlApiException("musicdl 响应解析失败")
        }
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        require(normalizedBase.isNotBlank()) { "请先配置 musicdl API 地址" }
        return "$normalizedBase/$path"
    }

    private fun String.errorSuffix(): String {
        val trimmed = trim()
        return if (trimmed.isBlank()) "" else "：$trimmed"
    }
}
