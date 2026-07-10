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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItemRow(
    song: LibrarySongItem,
    isActive: Boolean,
    isPlayingResponse: Boolean,
    transferState: SongTransferState,
    onSelection: () -> Unit,
    onTransfer: () -> Unit,
    onPlaybackToggle: () -> Unit,
    onShowDetails: () -> Unit,
    cardBg: Color,
    activeBg: Color,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    val isRunning = transferState is SongTransferState.Running
    val actionIcon = when {
        isPlayingResponse -> Icons.Filled.Equalizer
        song.isDownloaded -> Icons.Filled.Audiotrack
        song.canBackup -> Icons.Filled.CloudUpload
        song.canDownload -> Icons.Filled.CloudDownload
        else -> Icons.Filled.Warning
    }
    val availabilityLabel = when (song.status) {
        LibrarySongStatus.LOCAL_ONLY -> "仅本地"
        LibrarySongStatus.REMOTE_ONLY -> "未下载"
        LibrarySongStatus.BACKED_UP -> "已备份"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !isRunning,
                onClick = onSelection,
                onLongClick = {
                    if (song.isDownloaded) onShowDetails()
                }
            )
            .testTag("song_item_${song.title}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) Color(0x24D0BCFF) else Color(0x09FFFFFF),
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    colors = listOf(accentColor, Color(0x00FFFFFF))
                )
            )
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0x0AFFFFFF))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkThumbnail(
                filePath = song.localSong?.localPath,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)),
                placeholderIcon = actionIcon,
                placeholderTint = if (isPlayingResponse || isActive) accentColor else textDim,
                placeholderDescription = if (isPlayingResponse) "Playing index wave" else "Source icon"
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isActive) accentColor else textWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = song.artist,
                        color = textDim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = true)
                        )
                    )
                    Text(
                        text = availabilityLabel,
                        color = if (song.isDownloaded) Color(0xFFEADDFF).copy(alpha = 0.8f) else accentColor.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(
                                color = (if (song.isDownloaded) Color(0xFFEADDFF) else accentColor).copy(alpha = 0.08f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            if (isRunning || transferState is SongTransferState.Failed || song.canDownload || song.canBackup) {
                TransferActionIndicator(
                    state = transferState,
                    defaultDirection = when {
                        song.canBackup -> SongTransferDirection.UPLOAD
                        else -> SongTransferDirection.DOWNLOAD
                    },
                    accentColor = accentColor,
                    errorColor = Color(0xFFFF6B6B),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = !isRunning, onClick = onTransfer)
                        .testTag("song_transfer_${song.title}")
                )
            } else {
                IconButton(
                    onClick = onPlaybackToggle,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("song_play_pause_${song.title}")
                ) {
                    Icon(
                        imageVector = if (isPlayingResponse) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlayingResponse) "暂停" else "播放",
                        tint = if (isActive) accentColor else textDim,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferActionIndicator(
    state: SongTransferState,
    defaultDirection: SongTransferDirection,
    accentColor: Color,
    errorColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is SongTransferState.Running -> {
                val progress = state.progress?.coerceIn(0f, 1f)
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 2.dp,
                        trackColor = accentColor.copy(alpha = 0.18f)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 2.dp,
                        trackColor = accentColor.copy(alpha = 0.18f)
                    )
                }
            }

            is SongTransferState.Failed -> {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = if (state.direction == SongTransferDirection.UPLOAD) "Upload failed" else "Download failed",
                    tint = errorColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            SongTransferState.Idle -> {
                Icon(
                    imageVector = if (defaultDirection == SongTransferDirection.UPLOAD) Icons.Filled.CloudUpload else Icons.Filled.CloudDownload,
                    contentDescription = if (defaultDirection == SongTransferDirection.UPLOAD) "Upload track" else "Download track",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PlaylistSection(
    songs: List<LibrarySongItem>,
    currentSongMd5: String?,
    isPlaying: Boolean,
    transferStates: Map<String, SongTransferState>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelection: (LibrarySongItem, SongTransferState) -> Unit,
    onTransfer: (LibrarySongItem, SongTransferState) -> Unit,
    onPlaybackToggle: (LibrarySongItem) -> Unit,
    onShowDetails: (LibrarySongItem) -> Unit,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x0AFFFFFF),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "播放列表 (${songs.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textWhite,
                        letterSpacing = 0.5.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = accentColor
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x08FFFFFF))
                            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "还没有歌曲，导入本地音频或先同步云端歌单",
                            color = textDim,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(songs) { song ->
                            val isActive = currentSongMd5 == song.md5sum
                            val transferState = transferStates[song.md5sum] ?: SongTransferState.Idle
                            SongItemRow(
                                song = song,
                                isActive = isActive,
                                isPlayingResponse = isPlaying && isActive,
                                transferState = transferState,
                                onSelection = { onSelection(song, transferState) },
                                onTransfer = { onTransfer(song, transferState) },
                                onPlaybackToggle = { onPlaybackToggle(song) },
                                onShowDetails = { onShowDetails(song) },
                                cardBg = Color(0x09FFFFFF),
                                activeBg = Color(0x24D0BCFF),
                                accentColor = accentColor,
                                textWhite = textWhite,
                                textDim = textDim
                            )
                        }
                    }
                }
            }
        }
    }
}
