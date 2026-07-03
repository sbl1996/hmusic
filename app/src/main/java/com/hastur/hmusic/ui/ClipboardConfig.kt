package com.hastur.hmusic.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hastur.hmusic.data.BackupProfileEntity
import com.hastur.hmusic.player.LoopMode
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ParsedEnvConfig(
    val endpoint: String? = null,
    val region: String? = null,
    val forcePathStyle: Boolean? = null,
    val bucket: String? = null,
    val accessKeyId: String? = null,
    val accessKeySecret: String? = null,
    val prefix: String? = null
)

internal data class DecodedClipboardConfig(
    val plainText: String,
    val wasEncrypted: Boolean
)

internal data class ClipboardConfigCandidate(
    val parsed: ParsedEnvConfig,
    val wasEncrypted: Boolean,
    val hash: String
)

private fun parseEnvClipboardConfig(text: String): ParsedEnvConfig? {
    if (text.isBlank()) return null

    val pairs = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val key = line.substring(0, index).trim()
            val value = line.substring(index + 1).trim().trim('"', '\'')
            key to value
        }
        .toMap()

    if (pairs.isEmpty()) return null

    val parsed = ParsedEnvConfig(
        endpoint = pairs["S3_ENDPOINT"]?.takeIf { it.isNotBlank() },
        region = pairs["S3_REGION"]?.takeIf { it.isNotBlank() },
        forcePathStyle = pairs["S3_FORCE_PATH_STYLE"]?.lowercase(Locale.ROOT)?.let {
            when (it) {
                "true", "1", "yes", "y" -> true
                "false", "0", "no", "n" -> false
                else -> null
            }
        },
        bucket = pairs["S3_BUCKET"]?.takeIf { it.isNotBlank() },
        accessKeyId = pairs["S3_ACCESS_KEY_ID"]?.takeIf { it.isNotBlank() },
        accessKeySecret = pairs["S3_SECRET_ACCESS_KEY"]?.takeIf { it.isNotBlank() },
        prefix = pairs["S3_PREFIX"]?.takeIf { it.isNotBlank() }
    )

    return if (
        parsed.endpoint == null &&
        parsed.region == null &&
        parsed.forcePathStyle == null &&
        parsed.bucket == null &&
        parsed.accessKeyId == null &&
        parsed.accessKeySecret == null &&
        parsed.prefix == null
    ) {
        null
    } else {
        parsed
    }
}

internal fun buildSuggestedProfileName(config: ParsedEnvConfig): String {
    val bucket = config.bucket.orEmpty()
    val prefix = config.prefix.orEmpty().trim('/').substringBefore('/')
    return when {
        bucket.isNotBlank() && prefix.isNotBlank() -> "$bucket/$prefix"
        bucket.isNotBlank() -> bucket
        config.endpoint != null -> config.endpoint.removePrefix("https://").removePrefix("http://")
        else -> "新配置"
    }
}

private fun canonicalClipboardConfig(config: ParsedEnvConfig): String {
    return listOf(
        "S3_ENDPOINT=${config.endpoint.orEmpty()}",
        "S3_REGION=${config.region.orEmpty()}",
        "S3_FORCE_PATH_STYLE=${config.forcePathStyle?.toString().orEmpty()}",
        "S3_BUCKET=${config.bucket.orEmpty()}",
        "S3_ACCESS_KEY_ID=${config.accessKeyId.orEmpty()}",
        "S3_SECRET_ACCESS_KEY=${config.accessKeySecret.orEmpty()}",
        "S3_PREFIX=${config.prefix.orEmpty()}"
    ).joinToString(separator = "\n")
}

private fun clipboardConfigHash(config: ParsedEnvConfig): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(canonicalClipboardConfig(config).toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun buildClipboardConfigCandidate(decodedConfig: DecodedClipboardConfig): ClipboardConfigCandidate? {
    val parsed = parseEnvClipboardConfig(decodedConfig.plainText) ?: return null
    return ClipboardConfigCandidate(
        parsed = parsed,
        wasEncrypted = decodedConfig.wasEncrypted,
        hash = clipboardConfigHash(parsed)
    )
}

internal suspend fun readClipboardConfigCandidate(context: android.content.Context): ClipboardConfigCandidate? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val rawText = clipboard?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        .orEmpty()
    val decoded = decodeClipboardConfig(rawText) ?: return null
    return buildClipboardConfigCandidate(decoded)
}

internal fun shouldSuggestClipboardConfig(candidateHash: String, dismissedHash: String?): Boolean {
    return candidateHash.isNotBlank() && candidateHash != dismissedHash
}

private const val ENCRYPTED_CONFIG_PREFIX = "HMUSIC_CFG_V1:"
private const val CLIPBOARD_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val CLIPBOARD_IV_SIZE_BYTES = 12
private const val CLIPBOARD_TAG_SIZE_BITS = 128
private const val CLIPBOARD_KEY_MATERIAL = "hmusic-config-share-v1::clipboard"

internal fun decodeClipboardConfig(text: String): DecodedClipboardConfig? {
    if (!text.startsWith(ENCRYPTED_CONFIG_PREFIX)) {
        return DecodedClipboardConfig(plainText = text, wasEncrypted = false)
    }

    val payload = text.removePrefix(ENCRYPTED_CONFIG_PREFIX)
    val decodedBytes = try {
        Base64.decode(payload, Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        return null
    }

    if (decodedBytes.size <= CLIPBOARD_IV_SIZE_BYTES) return null

    val iv = decodedBytes.copyOfRange(0, CLIPBOARD_IV_SIZE_BYTES)
    val cipherBytes = decodedBytes.copyOfRange(CLIPBOARD_IV_SIZE_BYTES, decodedBytes.size)

    val plainBytes = try {
        val cipher = Cipher.getInstance(CLIPBOARD_CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            clipboardSecretKey(),
            GCMParameterSpec(CLIPBOARD_TAG_SIZE_BITS, iv)
        )
        cipher.doFinal(cipherBytes)
    } catch (_: Exception) {
        return null
    }

    return DecodedClipboardConfig(
        plainText = plainBytes.toString(StandardCharsets.UTF_8),
        wasEncrypted = true
    )
}

internal fun encryptClipboardConfig(text: String): String {
    val iv = ByteArray(CLIPBOARD_IV_SIZE_BYTES)
    SecureRandom().nextBytes(iv)

    val cipher = Cipher.getInstance(CLIPBOARD_CIPHER_TRANSFORMATION)
    cipher.init(
        Cipher.ENCRYPT_MODE,
        clipboardSecretKey(),
        GCMParameterSpec(CLIPBOARD_TAG_SIZE_BITS, iv)
    )

    val cipherBytes = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
    val payload = iv + cipherBytes
    val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
    return ENCRYPTED_CONFIG_PREFIX + encoded
}

private fun clipboardSecretKey(): SecretKeySpec {
    val keyBytes = MessageDigest.getInstance("SHA-256")
        .digest(CLIPBOARD_KEY_MATERIAL.toByteArray(StandardCharsets.UTF_8))
    return SecretKeySpec(keyBytes, "AES")
}

internal fun buildEnvClipboardConfig(
    endpoint: String,
    region: String,
    forcePathStyle: Boolean,
    bucket: String,
    accessKeyId: String,
    accessKeySecret: String,
    prefix: String
): String {
    return listOf(
        "S3_ENDPOINT=$endpoint",
        "S3_REGION=$region",
        "S3_FORCE_PATH_STYLE=$forcePathStyle",
        "S3_BUCKET=$bucket",
        "S3_ACCESS_KEY_ID=$accessKeyId",
        "S3_SECRET_ACCESS_KEY=$accessKeySecret",
        "S3_PREFIX=$prefix"
    ).joinToString(separator = "\n")
}
