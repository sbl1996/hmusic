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
import androidx.compose.material.icons.automirrored.filled.ManageSearch
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
fun AppSettingsPage(
    profiles: List<BackupProfileEntity>,
    activeProfile: BackupProfileEntity?,
    statusMessageState: StatusMessageState,
    localScanState: LocalScanState,
    musicdlBaseUrl: String,
    onSave: (String, String, String, Boolean, String, String, String, String) -> Unit,
    onMusicdlBaseUrlChange: (String) -> Unit,
    onCreateProfile: (Boolean) -> Unit,
    onSwitchProfile: (Long) -> Unit,
    onDeleteProfile: () -> Unit,
    onSync: () -> Unit,
    onDismissStatusMessage: () -> Unit,
    onShowStatusCompleted: (String) -> Unit,
    onShowStatusError: (String) -> Unit,
    hasSongDirectory: Boolean,
    onChooseSongDirectory: () -> Unit,
    onImportLocalSong: () -> Unit,
    onScanLocalSongs: () -> Unit,
    musicdlStorageState: MusicdlStorageUiState,
    onLoadDownloadStorage: () -> Unit,
    onCleanupDownloadStorage: () -> Unit,
    accentColor: Color,
    textWhite: Color,
    textDim: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "设置",
                color = textWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            SettingsSectionHeader(
                icon = Icons.Filled.Storage,
                title = "本地存储",
                accentColor = accentColor,
                textWhite = textWhite
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChooseSongDirectory),
                shape = RoundedCornerShape(20.dp),
                color = Color(0x13FFFFFF),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "歌曲存储目录",
                            color = textWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (hasSongDirectory) {
                                "/storage/emulated/0/Download/hmusic"
                            } else {
                                "未授权，请选择 Download/hmusic"
                            },
                            color = textDim,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    TextButton(onClick = onChooseSongDirectory) {
                        Text(if (hasSongDirectory) "更改" else "选择")
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = hasSongDirectory,
                        onClick = onImportLocalSong
                    )
                    .testTag("import_local_song_button"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0x0AFFFFFF),
                border = BorderStroke(1.dp, Color(0x14FFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = if (hasSongDirectory) accentColor else textDim,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "导入本地音乐",
                            color = if (hasSongDirectory) textWhite else textDim,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasSongDirectory) "从设备中选择音频文件" else "请先选择歌曲存储目录",
                            color = textDim,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = textDim
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = hasSongDirectory && !localScanState.isScanning,
                        onClick = onScanLocalSongs
                    )
                    .testTag("scan_local_songs_button"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0x0AFFFFFF),
                border = BorderStroke(1.dp, Color(0x14FFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (localScanState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = accentColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                            contentDescription = null,
                            tint = if (hasSongDirectory) accentColor else textDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (localScanState.isScanning) "正在扫描本地歌曲" else "扫描本地歌曲",
                            color = if (hasSongDirectory) textWhite else textDim,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                localScanState.isScanning && localScanState.total > 0 ->
                                    "${localScanState.scanned} / ${localScanState.total}"
                                hasSongDirectory -> "恢复目录中未加入播放列表的音频"
                                else -> "请先选择歌曲存储目录"
                            },
                            color = textDim,
                            fontSize = 12.sp
                        )
                    }
                    if (!localScanState.isScanning) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = textDim
                        )
                    }
                }
            }

            SettingsSectionHeader(
                icon = Icons.Filled.Cloud,
                title = "云端备份",
                accentColor = accentColor,
                textWhite = textWhite
            )

            OssSettingsCard(
                profiles = profiles,
                activeProfile = activeProfile,
                onSave = onSave,
                onCreateProfile = onCreateProfile,
                onSwitchProfile = onSwitchProfile,
                onDeleteProfile = onDeleteProfile,
                onSync = onSync,
                onShowStatusCompleted = onShowStatusCompleted,
                onShowStatusError = onShowStatusError,
                accentColor = accentColor,
                textWhite = textWhite,
                textDim = textDim
            )

            SettingsSectionHeader(
                icon = Icons.Filled.Search,
                title = "在线搜索",
                accentColor = accentColor,
                textWhite = textWhite
            )

            MusicdlSettingsCard(
                baseUrl = musicdlBaseUrl,
                onBaseUrlChange = onMusicdlBaseUrlChange,
                accentColor = accentColor,
                textWhite = textWhite,
                textDim = textDim
            )

            DownloadStorageCard(
                state = musicdlStorageState,
                onLoad = onLoadDownloadStorage,
                onCleanup = onCleanupDownloadStorage,
                accentColor = accentColor,
                textWhite = textWhite,
                textDim = textDim
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (statusMessageState != StatusMessageState.Idle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .zIndex(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                StatusMessageBanner(
                    statusMessageState = statusMessageState,
                    accentColor = accentColor,
                    textWhite = textWhite,
                    textDim = textDim,
                    onDismiss = onDismissStatusMessage,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    textWhite: Color
) {
    Row(
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = textWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MusicdlSettingsCard(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x0AFFFFFF),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "在线搜索服务",
                    color = textWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("服务地址") },
                placeholder = { Text("http://10.0.2.2:8000") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textWhite,
                    unfocusedTextColor = textWhite,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0x1EFFFFFF),
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = textDim,
                    focusedPlaceholderColor = textDim.copy(alpha = 0.5f),
                    unfocusedPlaceholderColor = textDim.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("musicdl_base_url_input")
            )
        }
    }
}

@Composable
private fun DownloadStorageCard(
    state: MusicdlStorageUiState,
    onLoad: () -> Unit,
    onCleanup: () -> Unit,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    var showCleanupConfirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { onLoad() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x0AFFFFFF),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CleaningServices,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "下载缓存",
                    color = textWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val usageText = when {
                state.isLoading -> "正在统计…"
                state.error != null -> state.error
                else -> "${formatStorageBytes(state.usedBytes)} / ${state.fileCount} 个文件"
            }
            Text(
                text = usageText,
                color = if (state.error != null) textWhite else textDim,
                fontSize = 13.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onLoad,
                    enabled = !state.isLoading && !state.isCleaning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("刷新", color = textWhite)
                }
                Button(
                    onClick = { showCleanupConfirm = true },
                    enabled = !state.isLoading && !state.isCleaning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF21005D)
                    )
                ) {
                    if (state.isCleaning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF21005D)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("清理", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showCleanupConfirm) {
        ConfirmationDialog(
            title = "清理下载缓存",
            message = "将删除服务端已完成/失败的下载任务目录，进行中的任务会保留。确定继续吗？",
            confirmLabel = "清理",
            dismissLabel = "取消",
            onConfirm = {
                showCleanupConfirm = false
                onCleanup()
            },
            onDismiss = { showCleanupConfirm = false }
        )
    }
}

private fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
}
