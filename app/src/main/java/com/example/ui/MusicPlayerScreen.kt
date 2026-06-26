package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.OssConfigEntity
import com.example.data.SongEntity
import com.example.player.LoopMode
import java.util.Locale

@Composable
fun MusicPlayerScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.allSongs.collectAsState()
    val ossConfig by viewModel.ossConfig.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val loopMode by viewModel.loopMode.collectAsState()
    val playerError by viewModel.playerError.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf("") }
    var inputTitle by remember { mutableStateOf("") }
    var inputArtist by remember { mutableStateOf("") }

    // Audio file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri.toString()
            val lastSegment = uri.path?.substringAfterLast("/") ?: ""
            inputTitle = lastSegment.substringBeforeLast(".").ifEmpty { "我的本地音乐" }
            inputArtist = "本地乐迷"
            showAddDialog = true
        }
    }

    // Frosted Glass Premium Aesthetic Theme colors
    val darkBackground = Color(0xFF0F1113)
    val cardBackground = Color(0x13FFFFFF) // Translucent glass base
    val accentNeonColor = Color(0xFFD0BCFF) // Elegant Lavender Accent
    val accentSoftGreen = Color(0xFFEADDFF) // Muted pastel purple
    val textWhite = Color(0xFFE2E2E6) // Soft high contrast white
    val textDim = Color(0xFF909094) // Muted gray description text

    // Infinite rotations/pulses for spinning vinyl
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val spinningAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPlaying) 1.06f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkBackground)
    ) {
        // Canvas drawing the underlying custom glowing spheres
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x28381E72), Color.Transparent),
                    radius = size.width * 1.0f
                ),
                radius = size.width * 1.0f,
                center = androidx.compose.ui.geometry.Offset(x = size.width * 0.1f, y = size.height * 0.25f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1AD0BCFF), Color.Transparent),
                    radius = size.width * 0.9f
                ),
                radius = size.width * 0.9f,
                center = androidx.compose.ui.geometry.Offset(x = size.width * 0.9f, y = size.height * 0.75f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 1. Top Bar Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "極簡音軌",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = textWhite,
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentNeonColor)
                        )
                        Text(
                            text = "S3 CLOUD SYNCED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentNeonColor,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // OSS Settings Toggle icon
                    IconButton(
                        onClick = { showSettings = !showSettings },
                        modifier = Modifier
                            .testTag("settings_button")
                            .clip(CircleShape)
                            .background(Color(0x13FFFFFF))
                            .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = "OSS Settings",
                            tint = if (showSettings) accentNeonColor else textWhite
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Local picker button
                    IconButton(
                        onClick = { filePickerLauncher.launch("audio/*") },
                        modifier = Modifier
                            .testTag("add_song_button")
                            .clip(CircleShape)
                            .background(Color(0x13FFFFFF))
                            .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Pick File",
                            tint = accentNeonColor
                        )
                    }
                }
            }

            // Sync notifications bar
            AnimatedVisibility(
                visible = syncState != SyncState.Idle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = when (syncState) {
                        is SyncState.Loading -> Color(0x1AD0BCFF)
                        is SyncState.Completed -> Color(0x1AB3FFD6)
                        else -> Color(0x1AFF5252)
                    },
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = Brush.linearGradient(
                            listOf(
                                when (syncState) {
                                    is SyncState.Loading -> accentNeonColor
                                    is SyncState.Completed -> Color(0xFFB3FFD6)
                                    else -> Color(0xFFFF5252)
                                }, Color.Transparent
                            )
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (syncState is SyncState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = accentNeonColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "正在与云端 S3 / OSS 同步中...",
                                color = textWhite,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(
                                imageVector = if (syncState is SyncState.Completed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = "Status",
                                tint = if (syncState is SyncState.Completed) Color(0xFFB3FFD6) else Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (val s = syncState) {
                                    is SyncState.Completed -> s.message
                                    is SyncState.Error -> s.message
                                    else -> ""
                                },
                                color = textWhite,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.resetSyncState() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    tint = textDim,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Expanded Settings layout
            AnimatedVisibility(
                visible = showSettings,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + shrinkVertically()
            ) {
                OssSettingsCard(
                    config = ossConfig,
                    syncState = syncState,
                    onSave = { endpoint, region, forcePathStyle, bucket, ak, sk, prefix ->
                        viewModel.saveOssConfig(endpoint, region, forcePathStyle, bucket, ak, sk, prefix)
                    },
                    onBackup = { viewModel.backupPlaylistToOSS() },
                    onRestore = { viewModel.restorePlaylistFromCloud() },
                    onSync = { viewModel.syncFromOSS() },
                    onClose = { showSettings = false },
                    onClearPlaylist = { viewModel.clearPlaylist() },
                    cardBackground = cardBackground,
                    accentColor = accentNeonColor,
                    textWhite = textWhite,
                    textDim = textDim
                )
            }

            // Playlist + Player layout
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Turntable cover
                if (!showSettings) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val currentSongName = currentSong?.title ?: "无正在播放的歌曲"

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.size(240.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background ambient glow
                                Box(
                                    modifier = Modifier
                                        .size(200.dp)
                                        .rotate(6f)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF381E72), Color(0xFFD0BCFF))
                                            ),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .align(Alignment.Center)
                                )

                                // Frosted Glass container overlay
                                Box(
                                    modifier = Modifier
                                        .size(198.dp)
                                        .background(
                                            color = Color(0x3D000000),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0x33FFFFFF), Color(0x0EFFFFFF))
                                            ),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF))
                                            ),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .align(Alignment.Center),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Beautiful inner vinyl disk that spins
                                    Box(
                                        modifier = Modifier
                                            .size(170.dp * breathingScale)
                                            .rotate(if (isPlaying) spinningAngle else 0f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Vinyl plate drawings
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            // Outer vinyl plate
                                            drawCircle(color = Color(0xFF131318), radius = size.width / 2.0f)
                                            // Ring grooves for reflection
                                            drawCircle(color = Color(0x22FFFFFF), radius = size.width / 2.2f, style = Stroke(1.0f))
                                            drawCircle(color = Color(0x12909094), radius = size.width / 2.5f, style = Stroke(1.5f))
                                            drawCircle(color = Color(0x12FFFFFF), radius = size.width / 3.0f, style = Stroke(1.5f))
                                            // Center hub
                                            drawCircle(color = Color(0xFF2C2A33), radius = size.width / 4.2f)
                                            // Dynamic Lavender Accent dot
                                            drawCircle(color = Color(0xFFD0BCFF), radius = size.width / 12f)
                                        }

                                        // Center core Icon overlay
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.35f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MusicNote,
                                                contentDescription = "Spinning disk icon",
                                                tint = Color(0xFFD0BCFF),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Custom labels
                            Text(
                                text = currentSongName,
                                color = textWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentSong?.artist ?: "请从下方列表挑选音轨",
                                color = accentNeonColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Error message if any
                playerError?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                // 2. Playback Timeline Dragging Controller
                PlaybackControlTimeline(
                    currentPos = currentPosition,
                    trackDuration = duration,
                    onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                    accentColor = accentNeonColor,
                    textWhite = textWhite,
                    textDim = textDim
                )

                // 3. Playback control actions
                PlaybackControlsRow(
                    isPlaying = isPlaying,
                    loopMode = loopMode,
                    onPlayPause = { viewModel.playOrPause() },
                    onPrev = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() },
                    onToggleLoop = { viewModel.toggleLoopMode() },
                    accentColor = accentNeonColor,
                    cardBackground = Color(0x1F44474E),
                    textWhite = textWhite
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Playlist catalog
                Text(
                    text = "共享播放列表 (${songs.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textWhite,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x0AFFFFFF))
                            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "列表空，正在初始化默认歌曲音轨...",
                            color = textDim,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(songs) { song ->
                            val isActive = currentSong?.url == song.url
                            SongItemRow(
                                song = song,
                                isActive = isActive,
                                isPlayingResponse = isPlaying && isActive,
                                onSelection = { viewModel.playSong(song) },
                                onDelete = { viewModel.deleteSong(song) },
                                cardBg = Color(0x09FFFFFF),
                                activeBg = Color(0x24D0BCFF),
                                accentColor = accentNeonColor,
                                textWhite = textWhite,
                                textDim = textDim
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Modal dialog to add a file
    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1A1D24),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1F909094))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "解析本地音乐",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textWhite,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("音乐标题") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textWhite,
                            unfocusedTextColor = textWhite,
                            focusedBorderColor = accentNeonColor,
                            unfocusedBorderColor = Color(0x1EFFFFFF),
                            focusedLabelColor = accentNeonColor,
                            unfocusedLabelColor = textDim
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("add_song_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputArtist,
                        onValueChange = { inputArtist = it },
                        label = { Text("歌手/艺术家") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textWhite,
                            unfocusedTextColor = textWhite,
                            focusedBorderColor = accentNeonColor,
                            unfocusedBorderColor = Color(0x1EFFFFFF),
                            focusedLabelColor = accentNeonColor,
                            unfocusedLabelColor = textDim
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("add_song_artist_input")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("取消", color = accentNeonColor)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (inputTitle.isNotBlank()) {
                                    viewModel.addLocalSong(inputTitle, inputArtist, pickedUri)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentSoftGreen,
                                contentColor = Color(0xFF21005D)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("save_song_dialog_button")
                        ) {
                            Text("添加并播放", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongItemRow(
    song: SongEntity,
    isActive: Boolean,
    isPlayingResponse: Boolean,
    onSelection: () -> Unit,
    onDelete: () -> Unit,
    cardBg: Color,
    activeBg: Color,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelection() }
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
            // Icon layout based on source provider
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPlayingResponse) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Playing index wave",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (song.isLocal) Icons.Filled.Audiotrack else Icons.Filled.CloudQueue,
                        contentDescription = "Source icon",
                        tint = if (isActive) accentColor else textDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (song.isLocal) "本地" else "云端 S3/OSS",
                        color = if (song.isLocal) Color(0xFFEADDFF).copy(alpha = 0.8f) else accentColor.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(
                                color = (if (song.isLocal) Color(0xFFEADDFF) else accentColor).copy(alpha = 0.08f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Quick play indicator or Delete action
            if (isActive) {
                Icon(
                    imageVector = if (isPlayingResponse) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Quick action",
                    tint = accentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove track",
                        tint = textDim.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaybackControlTimeline(
    currentPos: Long,
    trackDuration: Long,
    onSeek: (Long) -> Unit,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    var isDragging by remember { mutableStateOf(false) }
    var draggingValueByMs by remember { mutableStateOf(0f) }

    val resolvedDuration = if (trackDuration <= 0L) {
        if (currentPos > 0L) currentPos + 10000L else 180000L // 3 mins fallback
    } else trackDuration

    val progressValue = if (isDragging) draggingValueByMs else currentPos.toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Slider(
            value = progressValue.coerceIn(0f, resolvedDuration.toFloat()),
            onValueChange = { newValue ->
                isDragging = true
                draggingValueByMs = newValue
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek(draggingValueByMs.toLong())
            },
            valueRange = 0f..resolvedDuration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .testTag("timeline_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(progressValue.toLong()),
                color = textDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = formatDuration(resolvedDuration),
                color = textDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun PlaybackControlsRow(
    isPlaying: Boolean,
    loopMode: LoopMode,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleLoop: () -> Unit,
    accentColor: Color,
    cardBackground: Color,
    textWhite: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Loop mode toggler
        IconButton(
            onClick = { onToggleLoop() },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x13FFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                .testTag("loop_mode_toggle")
        ) {
            Icon(
                imageVector = if (loopMode == LoopMode.SINGLE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Loop Mode",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        // Previous button
        IconButton(
            onClick = { onPrev() },
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x13FFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                .testTag("prev_button")
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Prev",
                tint = textWhite,
                modifier = Modifier.size(24.dp)
            )
        }

        // Play Pause hub (Glow accent from top, center prominent solid button)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFEADDFF), Color(0xFFD0BCFF))
                    )
                )
                .clickable { onPlayPause() }
                .testTag("play_pause_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "PlayPause",
                tint = Color(0xFF21005D),
                modifier = Modifier.size(36.dp)
            )
        }

        // Next button
        IconButton(
            onClick = { onNext() },
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x13FFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                .testTag("next_button")
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = textWhite,
                modifier = Modifier.size(24.dp)
            )
        }

        // Label representing LoopMode
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x13FFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (loopMode == LoopMode.SINGLE) "单曲" else "列表",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun OssSettingsCard(
    config: OssConfigEntity?,
    syncState: SyncState,
    onSave: (String, String, Boolean, String, String, String, String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSync: () -> Unit,
    onClose: () -> Unit,
    onClearPlaylist: () -> Unit,
    cardBackground: Color,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    var endpoint by remember(config) { mutableStateOf(config?.endpoint ?: "") }
    var region by remember(config) { mutableStateOf(config?.region ?: "") }
    var forcePathStyle by remember(config) { mutableStateOf(config?.forcePathStyle ?: false) }
    var bucket by remember(config) { mutableStateOf(config?.bucket ?: "") }
    var ak by remember(config) { mutableStateOf(config?.accessKeyId ?: "") }
    var sk by remember(config) { mutableStateOf(config?.accessKeySecret ?: "") }
    var prefix by remember(config) { mutableStateOf(config?.prefix ?: "") }
    var skVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x17FFFFFF)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = "OSS Settings Icon",
                        tint = accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "S3 / 七牛云 / OSS 账号同步配置",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textWhite
                    )
                }

                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close config",
                        tint = textDim
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("S3 Endpoint (地域节点域名)") },
                placeholder = { Text("s3-cn-east-1.qiniucs.com 或 oss-cn-hangzhou.aliyuncs.com") },
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
                modifier = Modifier.fillMaxWidth().testTag("oss_endpoint_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = region,
                onValueChange = { region = it },
                label = { Text("Region (签名地域)") },
                placeholder = { Text("cn-east-1 / cn-hangzhou / us-east-1") },
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
                modifier = Modifier.fillMaxWidth().testTag("oss_region_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x10FFFFFF))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "强制 Path-Style",
                        color = textWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "七牛、MinIO、本地 IP/端口 常需要开启；阿里云 OSS 通常保持关闭",
                        color = textDim,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = forcePathStyle,
                    onCheckedChange = { forcePathStyle = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = bucket,
                onValueChange = { bucket = it },
                label = { Text("Bucket 存储桶名称") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textWhite,
                    unfocusedTextColor = textWhite,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0x1EFFFFFF),
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = textDim
                ),
                modifier = Modifier.fillMaxWidth().testTag("oss_bucket_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ak,
                onValueChange = { ak = it },
                label = { Text("AccessKey ID (支持 Public 免密拉取)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textWhite,
                    unfocusedTextColor = textWhite,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0x1EFFFFFF),
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = textDim
                ),
                modifier = Modifier.fillMaxWidth().testTag("oss_ak_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = sk,
                onValueChange = { sk = it },
                label = { Text("AccessKey Secret") },
                singleLine = true,
                visualTransformation = if (skVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { skVisible = !skVisible }) {
                        Icon(
                            imageVector = if (skVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle Secret",
                            tint = textDim
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textWhite,
                    unfocusedTextColor = textWhite,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0x1EFFFFFF),
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = textDim
                ),
                modifier = Modifier.fillMaxWidth().testTag("oss_sk_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("扫描路径前缀 (如 music/)") },
                placeholder = { Text("music/") },
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
                modifier = Modifier.fillMaxWidth().testTag("oss_prefix_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onSave(endpoint, region, forcePathStyle, bucket, ak, sk, prefix) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF21005D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_settings_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = "Save settings", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("保存连接并同步云端目录", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF), contentColor = textWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cloud_backup_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.CloudUpload, contentDescription = "Backup", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("整单云备份", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF), contentColor = textWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cloud_restore_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = "Restore", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("整单云还原", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onClearPlaylist,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("clear_playlist_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = "Clear List",
                    tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("清空列表并初始化默认音轨", color = Color(0xFFFF5252).copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.ROOT, "%02d:%02d", mins, secs)
}
