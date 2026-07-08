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
fun MusicPlayerScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.allSongs.collectAsState()
    val backupProfiles by viewModel.backupProfiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val statusMessageState by viewModel.statusMessageState.collectAsState()
    val transferStates by viewModel.transferStates.collectAsState()
    val musicdlSearchState by viewModel.musicdlSearchState.collectAsState()
    val musicdlBaseUrl by viewModel.musicdlBaseUrl.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val loopMode by viewModel.loopMode.collectAsState()
    val playerError by viewModel.playerError.collectAsState()
    val bannerAutoDismissMillis = 3000L

    var showSettings by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf("") }
    var inputTitle by remember { mutableStateOf("") }
    var inputArtist by remember { mutableStateOf("") }
    var isPlaylistExpanded by rememberSaveable { mutableStateOf(true) }
    var pendingDeleteSong by remember { mutableStateOf<LibrarySongItem?>(null) }

    // Audio file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri.toString()
            val lastSegment = uri.path?.substringAfterLast("/") ?: ""
            inputTitle = lastSegment.substringBeforeLast(".").ifEmpty { "我的本地音乐" }
            inputArtist = "佚名"
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
                        onClick = {
                            showSettings = !showSettings
                            if (showSettings) showSearch = false
                        },
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

                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (showSearch) showSettings = false
                        },
                        modifier = Modifier
                            .testTag("search_button")
                            .clip(CircleShape)
                            .background(Color(0x13FFFFFF))
                            .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (showSearch) "Close search" else "Search musicdl",
                            tint = if (showSearch) accentNeonColor else textWhite
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
                    musicdlBaseUrl = musicdlBaseUrl,
                    onSave = { name, endpoint, region, forcePathStyle, bucket, ak, sk, prefix ->
                        viewModel.saveActiveProfile(name, endpoint, region, forcePathStyle, bucket, ak, sk, prefix)
                    },
                    onMusicdlBaseUrlChange = viewModel::updateMusicdlBaseUrl,
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
            } else if (showSearch) {
                SearchSection(
                    searchState = musicdlSearchState,
                    transferStates = transferStates,
                    onKeywordChange = viewModel::updateMusicdlKeyword,
                    onSearch = viewModel::searchMusicdl,
                    onDownload = viewModel::downloadMusicdlItem,
                    accentColor = accentNeonColor,
                    textWhite = textWhite,
                    textDim = textDim,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            } else {
                // Playlist + Player layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    PlayerArtworkSection(
                        modifier = Modifier.weight(if (isPlaylistExpanded) 1.2f else 2.05f),
                        currentSongTitle = currentSong?.title ?: "无正在播放的歌曲",
                        currentSongArtist = currentSong?.artist ?: "请从下方列表挑选音轨",
                        currentSongLocalPath = currentSong?.localPath,
                        isPlaying = isPlaying,
                        spinningAngle = spinningAngle,
                        breathingScale = breathingScale,
                        isPlaylistExpanded = isPlaylistExpanded,
                        accentColor = accentNeonColor,
                        textWhite = textWhite
                    )
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
                    isPlaylistExpanded = isPlaylistExpanded,
                    onTogglePlaylist = { isPlaylistExpanded = !isPlaylistExpanded },
                    accentColor = accentNeonColor,
                    textWhite = textWhite
                )

                Spacer(modifier = Modifier.height(12.dp))

                PlaylistSection(
                    songs = songs,
                    currentSongMd5 = currentSong?.md5sum,
                    isPlaying = isPlaying,
                    transferStates = transferStates,
                    isExpanded = isPlaylistExpanded,
                    onToggleExpanded = { isPlaylistExpanded = !isPlaylistExpanded },
                    onSelection = { song, transferState ->
                        if (transferState is SongTransferState.Running) {
                            Unit
                        } else if (song.isDownloaded) {
                            if (song.canBackup) {
                                viewModel.uploadSong(song)
                            } else {
                                viewModel.playSong(song)
                            }
                        } else {
                            viewModel.downloadSong(song)
                        }
                    },
                    onDelete = { song -> pendingDeleteSong = song },
                    accentColor = accentNeonColor,
                    textWhite = textWhite,
                    textDim = textDim
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            }
        }
    }

    pendingDeleteSong?.let { song ->
        ConfirmationDialog(
            title = "确认删除歌曲",
            message = "确定要删除「${song.title}」吗？此操作不可撤销。",
            confirmLabel = "删除",
            dismissLabel = "取消",
            onConfirm = {
                viewModel.deleteSong(song)
                pendingDeleteSong = null
            },
            onDismiss = { pendingDeleteSong = null }
        )
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
                        text = "导入本地音乐",
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
                            Text("导入", fontWeight = FontWeight.Bold)
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
