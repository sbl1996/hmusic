package com.hastur.hmusic.ui

import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MusicPlayerScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.allSongs.collectAsState()
    val backupProfiles by viewModel.backupProfiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val statusMessageState by viewModel.statusMessageState.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val loopMode by viewModel.loopMode.collectAsState()
    val playerError by viewModel.playerError.collectAsState()
    val bannerAutoDismissMillis = 3000L

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

    LaunchedEffect(statusMessageState) {
        when (statusMessageState) {
            is StatusMessageState.Completed, is StatusMessageState.Error -> {
                delay(bannerAutoDismissMillis)
                viewModel.resetStatusMessage()
            }
            else -> Unit
        }
    }

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
                        text = "Music",
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
                            text = "LOCAL PLAYBACK",
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
                            imageVector = if (showSettings) Icons.Filled.Close else Icons.Filled.Cloud,
                            contentDescription = if (showSettings) "Close settings" else "OSS Settings",
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

            if (showSettings) {
                BackupSettingsPage(
                    profiles = backupProfiles,
                    activeProfile = activeProfile,
                    statusMessageState = statusMessageState,
                    onSave = { name, endpoint, region, forcePathStyle, bucket, ak, sk, prefix ->
                        viewModel.saveActiveProfile(name, endpoint, region, forcePathStyle, bucket, ak, sk, prefix)
                    },
                    onCreateProfile = { copyCurrent -> viewModel.createProfile(copyCurrent) },
                    onSwitchProfile = { profileId -> viewModel.switchProfile(profileId) },
                    onDeleteProfile = { viewModel.deleteActiveProfile() },
                    onBackup = { viewModel.backupPlaylistToOSS() },
                    onSyncPlaylist = { viewModel.syncFromOSS() },
                    onSync = { viewModel.syncFromOSS() },
                    onClearPlaylist = { viewModel.clearPlaylist() },
                    onDismissStatusMessage = viewModel::resetStatusMessage,
                    onShowStatusCompleted = viewModel::showStatusCompleted,
                    onShowStatusError = viewModel::showStatusError,
                    accentColor = accentNeonColor,
                    textWhite = textWhite,
                    textDim = textDim,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Playlist + Player layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                // Turntable cover
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val currentSongName = currentSong?.title ?: "无正在播放的歌曲"
                        val artworkFrameSize = minOf(maxWidth * 0.62f, maxHeight * 0.68f, 240.dp)
                        val backgroundGlowSize = artworkFrameSize * 0.83f
                        val glassCardSize = artworkFrameSize * 0.825f
                        val vinylSize = artworkFrameSize * 0.71f
                        val artworkSize = artworkFrameSize * 0.46f

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                        ) {
                            Box(
                                modifier = Modifier.size(artworkFrameSize),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background ambient glow
                                Box(
                                    modifier = Modifier
                                        .size(backgroundGlowSize)
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
                                        .size(glassCardSize)
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
                                            .size(vinylSize * breathingScale)
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
                                        }

                                        ArtworkDisc(
                                            filePath = currentSong?.localPath,
                                            modifier = Modifier.size(artworkSize),
                                            accentColor = accentNeonColor,
                                            placeholderIcon = Icons.Filled.MusicNote,
                                            placeholderDescription = "Spinning disk icon"
                                        )
                                    }
                                }
                            }

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
                            Text(
                                text = currentSong?.artist ?: "请从下方列表挑选音轨",
                                color = accentNeonColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
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
                    text = "播放列表 (${songs.size})",
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
                            text = "还没有歌曲，导入本地音频或先同步云端歌单",
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
                            val isActive = currentSong?.md5sum == song.md5sum
                            val downloadState = downloadStates[song.md5sum] ?: SongDownloadState.Idle
                            SongItemRow(
                                song = song,
                                isActive = isActive,
                                isPlayingResponse = isPlaying && isActive,
                                downloadState = downloadState,
                                onSelection = {
                                    if (downloadState is SongDownloadState.Downloading) {
                                        Unit
                                    } else if (song.isDownloaded) {
                                        viewModel.playSong(song)
                                    } else {
                                        viewModel.downloadSong(song)
                                    }
                                },
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
                            Text("导入到本地", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!showSettings && statusMessageState != StatusMessageState.Idle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                    .zIndex(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                StatusMessageBanner(
                    statusMessageState = statusMessageState,
                    accentColor = accentNeonColor,
                    textWhite = textWhite,
                    textDim = textDim,
                    onDismiss = viewModel::resetStatusMessage
                )
            }
        }
    }
}

@Composable
fun BackupSettingsPage(
    profiles: List<BackupProfileEntity>,
    activeProfile: BackupProfileEntity?,
    statusMessageState: StatusMessageState,
    onSave: (String, String, String, Boolean, String, String, String, String) -> Unit,
    onCreateProfile: (Boolean) -> Unit,
    onSwitchProfile: (Long) -> Unit,
    onDeleteProfile: () -> Unit,
    onBackup: () -> Unit,
    onSyncPlaylist: () -> Unit,
    onSync: () -> Unit,
    onClearPlaylist: () -> Unit,
    onDismissStatusMessage: () -> Unit,
    onShowStatusCompleted: (String) -> Unit,
    onShowStatusError: (String) -> Unit,
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
            OssSettingsCard(
                profiles = profiles,
                activeProfile = activeProfile,
                onSave = onSave,
                onCreateProfile = onCreateProfile,
                onSwitchProfile = onSwitchProfile,
                onDeleteProfile = onDeleteProfile,
                onBackup = onBackup,
                onSyncPlaylist = onSyncPlaylist,
                onSync = onSync,
                onClearPlaylist = onClearPlaylist,
                onShowStatusCompleted = onShowStatusCompleted,
                onShowStatusError = onShowStatusError,
                cardBackground = Color(0x13FFFFFF),
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
fun SongItemRow(
    song: LibrarySongItem,
    isActive: Boolean,
    isPlayingResponse: Boolean,
    downloadState: SongDownloadState,
    onSelection: () -> Unit,
    onDelete: () -> Unit,
    cardBg: Color,
    activeBg: Color,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    val isDownloading = downloadState is SongDownloadState.Downloading
    val actionIcon = when {
        isPlayingResponse -> Icons.Filled.Equalizer
        song.isDownloaded -> Icons.Filled.Audiotrack
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
            .clickable(enabled = !isDownloading) { onSelection() }
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
                if (isDownloading || downloadState is SongDownloadState.Failed || song.canDownload) {
                    DownloadActionIndicator(
                        state = downloadState,
                        accentColor = accentColor,
                        errorColor = Color(0xFFFF6B6B),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
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
}

@Composable
private fun DownloadActionIndicator(
    state: SongDownloadState,
    accentColor: Color,
    errorColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is SongDownloadState.Downloading -> {
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

            is SongDownloadState.Failed -> {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Download failed",
                    tint = errorColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            SongDownloadState.Idle -> {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = "Download track",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtworkDisc(
    filePath: String?,
    modifier: Modifier,
    accentColor: Color,
    placeholderIcon: ImageVector,
    placeholderDescription: String
) {
    val artwork = rememberEmbeddedArtwork(filePath)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF2C2A33)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = placeholderDescription,
                    tint = accentColor,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtworkThumbnail(
    filePath: String?,
    modifier: Modifier,
    placeholderIcon: ImageVector,
    placeholderTint: Color,
    placeholderDescription: String
) {
    val artwork = rememberEmbeddedArtwork(filePath)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = "Song artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = placeholderDescription,
                tint = placeholderTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun rememberEmbeddedArtwork(filePath: String?): ImageBitmap? {
    val resolvedPath = filePath?.takeIf { it.isNotBlank() }
    return produceState<ImageBitmap?>(initialValue = null, key1 = resolvedPath) {
        value = if (resolvedPath == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(resolvedPath)
                        val bytes = retriever.embeddedPicture ?: return@runCatching null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } finally {
                        retriever.release()
                    }
                }.getOrNull()
            }
        }
    }.value
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
    profiles: List<BackupProfileEntity>,
    activeProfile: BackupProfileEntity?,
    onSave: (String, String, String, Boolean, String, String, String, String) -> Unit,
    onCreateProfile: (Boolean) -> Unit,
    onSwitchProfile: (Long) -> Unit,
    onDeleteProfile: () -> Unit,
    onBackup: () -> Unit,
    onSyncPlaylist: () -> Unit,
    onSync: () -> Unit,
    onClearPlaylist: () -> Unit,
    onShowStatusCompleted: (String) -> Unit,
    onShowStatusError: (String) -> Unit,
    cardBackground: Color,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    val context = LocalContext.current
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var profileName by remember(activeProfile) { mutableStateOf(activeProfile?.name ?: "") }
    var endpoint by remember(activeProfile) { mutableStateOf(activeProfile?.endpoint ?: "") }
    var region by remember(activeProfile) { mutableStateOf(activeProfile?.region ?: "") }
    var forcePathStyle by remember(activeProfile) { mutableStateOf(activeProfile?.forcePathStyle ?: false) }
    var bucket by remember(activeProfile) { mutableStateOf(activeProfile?.bucket ?: "") }
    var ak by remember(activeProfile) { mutableStateOf(activeProfile?.accessKeyId ?: "") }
    var sk by remember(activeProfile) { mutableStateOf(activeProfile?.accessKeySecret ?: "") }
    var prefix by remember(activeProfile) { mutableStateOf(activeProfile?.prefix ?: "") }
    var skVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { profileMenuExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x10FFFFFF),
                        border = BorderStroke(1.dp, Color(0x1AFFFFFF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = "Select profile",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeProfile?.name ?: "选择配置",
                                    color = textWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Expand profiles",
                                tint = textDim
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (profile.isActive) "${profile.name} · 当前" else profile.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    profileMenuExpanded = false
                                    onSwitchProfile(profile.id)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { onCreateProfile(false) },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x13FFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddCircle,
                        contentDescription = "Create profile",
                        tint = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text("配置名称") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textWhite,
                    unfocusedTextColor = textWhite,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0x1EFFFFFF),
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = textDim
                ),
                modifier = Modifier.fillMaxWidth().testTag("oss_profile_name_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                label = { Text("备份路径前缀 (如 music/)") },
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSave(profileName, endpoint, region, forcePathStyle, bucket, ak, sk, prefix) },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF21005D)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_settings_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Save, contentDescription = "Save settings", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存配置", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        val rawText = clipboard?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        val parsed = parseEnvClipboardConfig(rawText)
                        if (parsed == null) {
                            onShowStatusError("剪贴板里没有可解析的 S3 配置")
                            Toast.makeText(context, "剪贴板里没有可解析的 S3 配置", Toast.LENGTH_SHORT).show()
                        } else {
                            endpoint = parsed.endpoint ?: endpoint
                            region = parsed.region ?: region
                            forcePathStyle = parsed.forcePathStyle ?: forcePathStyle
                            bucket = parsed.bucket ?: bucket
                            ak = parsed.accessKeyId ?: ak
                            sk = parsed.accessKeySecret ?: sk
                            prefix = parsed.prefix ?: prefix
                            if (profileName.isBlank()) {
                                profileName = buildSuggestedProfileName(parsed)
                            }
                            onShowStatusCompleted("已从剪贴板填充配置")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF), contentColor = textWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.ContentPaste, contentDescription = "Paste config", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("粘贴配置", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    Text("备份到云端", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSyncPlaylist,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF), contentColor = textWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cloud_sync_playlist_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = "Full sync", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("完全同步", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSync,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF), contentColor = textWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Sync, contentDescription = "Sync manifest", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("仅同步列表", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onClearPlaylist,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF), contentColor = textWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clear_playlist_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = "Clear local list", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空本地列表", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDeleteProfile,
                enabled = profiles.size > 1,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x33B3261E),
                    contentColor = Color(0xFFFFDAD6),
                    disabledContainerColor = Color(0x14FFFFFF),
                    disabledContentColor = textDim
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (profiles.size > 1) Color(0x66FF8A80) else Color(0x1AFFFFFF)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete current profile", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("删除当前配置", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.ROOT, "%02d:%02d", mins, secs)
}

private data class ParsedEnvConfig(
    val endpoint: String? = null,
    val region: String? = null,
    val forcePathStyle: Boolean? = null,
    val bucket: String? = null,
    val accessKeyId: String? = null,
    val accessKeySecret: String? = null,
    val prefix: String? = null
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

private fun buildSuggestedProfileName(config: ParsedEnvConfig): String {
    val bucket = config.bucket.orEmpty()
    val prefix = config.prefix.orEmpty().trim('/').substringBefore('/')
    return when {
        bucket.isNotBlank() && prefix.isNotBlank() -> "$bucket/$prefix"
        bucket.isNotBlank() -> bucket
        config.endpoint != null -> config.endpoint.removePrefix("https://").removePrefix("http://")
        else -> "配置"
    }
}
