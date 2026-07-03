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

@Composable
fun StatusMessageBanner(
    statusMessageState: StatusMessageState,
    accentColor: Color,
    textWhite: Color,
    textDim: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (statusMessageState) {
        is StatusMessageState.Loading -> Color(0xFF1E2633)
        is StatusMessageState.Completed -> Color(0xFF1B2922)
        is StatusMessageState.Error -> Color(0xFF342020)
        StatusMessageState.Idle -> Color(0xFF1E2633)
    }
    val borderColor = when (statusMessageState) {
        is StatusMessageState.Loading -> accentColor.copy(alpha = 0.7f)
        is StatusMessageState.Completed -> Color(0xFF7ED9A8)
        is StatusMessageState.Error -> Color(0xFFFF8A80)
        StatusMessageState.Idle -> accentColor.copy(alpha = 0.7f)
    }
    val iconTint = when (statusMessageState) {
        is StatusMessageState.Loading -> accentColor
        is StatusMessageState.Completed -> Color(0xFFB8F5CF)
        is StatusMessageState.Error -> Color(0xFFFFB4AB)
        StatusMessageState.Idle -> accentColor
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (statusMessageState is StatusMessageState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = iconTint
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "正在同步歌单或文件...",
                    color = textWhite,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Icon(
                    imageVector = if (statusMessageState is StatusMessageState.Completed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = "Sync status",
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (val state = statusMessageState) {
                        is StatusMessageState.Completed -> state.message
                        is StatusMessageState.Error -> state.message
                        else -> ""
                    },
                    color = textWhite,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss sync status",
                        tint = textWhite.copy(alpha = 0.72f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
