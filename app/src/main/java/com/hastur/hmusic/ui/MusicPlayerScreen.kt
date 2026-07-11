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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
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
import java.io.File

private enum class PrimaryDestination(
    val label: String,
    val eyebrow: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Player("播放", "LOCAL PLAYBACK", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    Search("搜索", "DISCOVER MUSIC", Icons.Filled.Search, Icons.Outlined.Search),
    Settings("设置", "APP SETTINGS", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MusicPlayerScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiPreferencesStore = remember(context) { UiPreferencesStore(context) }
    val useIconBottomNavigation by uiPreferencesStore.useIconBottomNavigation.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()
    val songs by viewModel.allSongs.collectAsState()
    val backupProfiles by viewModel.backupProfiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val statusMessageState by viewModel.statusMessageState.collectAsState()
    val transferStates by viewModel.transferStates.collectAsState()
    val musicdlSearchState by viewModel.musicdlSearchState.collectAsState()
    val musicdlBaseUrl by viewModel.musicdlBaseUrl.collectAsState()
    val localScanState by viewModel.localScanState.collectAsState()
    val musicdlStorageState by viewModel.musicdlStorageState.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val loopMode by viewModel.loopMode.collectAsState()
    val playerError by viewModel.playerError.collectAsState()
    val bannerAutoDismissMillis = 3000L

    var primaryDestination by rememberSaveable { mutableStateOf(PrimaryDestination.Player) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf("") }
    var inputTitle by remember { mutableStateOf("") }
    var inputArtist by remember { mutableStateOf("") }
    var isPlaylistExpanded by rememberSaveable { mutableStateOf(true) }
    var detailsSong by remember { mutableStateOf<LibrarySongItem?>(null) }
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }

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
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching { viewModel.configureSongDirectory(uri) }
                .onSuccess { pendingStorageAction?.invoke() }
                .onFailure {
                    Toast.makeText(context, it.localizedMessage ?: "目录授权失败", Toast.LENGTH_LONG).show()
                }
        }
        pendingStorageAction = null
    }
    val runWithSongDirectory: (() -> Unit) -> Unit = { action ->
        if (viewModel.hasSongDirectory()) {
            action()
        } else {
            pendingStorageAction = action
            Toast.makeText(
                context,
                "请选择 Download/hmusic 目录；没有该目录时可在选择器中创建",
                Toast.LENGTH_LONG
            ).show()
            directoryPickerLauncher.launch(null)
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
                .padding(bottom = 56.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Brand header. Primary navigation stays in the stable bottom bar.
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
                            text = primaryDestination.eyebrow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentNeonColor,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

            }

            if (primaryDestination == PrimaryDestination.Settings) {
                AppSettingsPage(
                    profiles = backupProfiles,
                    activeProfile = activeProfile,
                    statusMessageState = statusMessageState,
                    localScanState = localScanState,
                    musicdlBaseUrl = musicdlBaseUrl,
                    onSave = { name, endpoint, region, forcePathStyle, bucket, ak, sk, prefix ->
                        viewModel.saveActiveProfile(name, endpoint, region, forcePathStyle, bucket, ak, sk, prefix)
                    },
                    onMusicdlBaseUrlChange = viewModel::updateMusicdlBaseUrl,
                    onCreateProfile = { copyCurrent -> viewModel.createProfile(copyCurrent) },
                    onSwitchProfile = { profileId -> viewModel.switchProfile(profileId) },
                    onDeleteProfile = { viewModel.deleteActiveProfile() },
                    onSync = { viewModel.syncFromOSS() },
                    onDismissStatusMessage = viewModel::resetStatusMessage,
                    onShowStatusCompleted = viewModel::showStatusCompleted,
                    onShowStatusError = viewModel::showStatusError,
                    hasSongDirectory = viewModel.hasSongDirectory(),
                    onChooseSongDirectory = {
                        pendingStorageAction = null
                        directoryPickerLauncher.launch(null)
                    },
                    onImportLocalSong = {
                        runWithSongDirectory { filePickerLauncher.launch("audio/*") }
                    },
                    onScanLocalSongs = viewModel::scanAndRestoreLocalSongs,
                    musicdlStorageState = musicdlStorageState,
                    onLoadDownloadStorage = viewModel::loadDownloadStorage,
                    onCleanupDownloadStorage = viewModel::cleanupDownloadStorage,
                    useIconBottomNavigation = useIconBottomNavigation,
                    onUseIconBottomNavigationChange = { enabled ->
                        coroutineScope.launch { uiPreferencesStore.setUseIconBottomNavigation(enabled) }
                    },
                    accentColor = accentNeonColor,
                    textWhite = textWhite,
                    textDim = textDim,
                    modifier = Modifier.weight(1f)
                )
            } else if (primaryDestination == PrimaryDestination.Search) {
                SearchSection(
                    searchState = musicdlSearchState,
                    transferStates = transferStates,
                    onKeywordChange = viewModel::updateMusicdlKeyword,
                    onSearch = viewModel::searchMusicdl,
                    onDownload = { item ->
                        runWithSongDirectory { viewModel.downloadMusicdlItem(item) }
                    },
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

                if (isPlaylistExpanded) {
                    PlaylistSection(
                        songs = songs,
                        currentSongMd5 = currentSong?.md5sum,
                        isPlaying = isPlaying,
                        transferStates = transferStates,
                        isExpanded = true,
                        onToggleExpanded = { isPlaylistExpanded = false },
                        onSelection = { song, transferState ->
                            if (transferState is SongTransferState.Running) {
                                Unit
                            } else if (song.isDownloaded) {
                                viewModel.playSong(song)
                            } else {
                                Unit
                            }
                        },
                        onTransfer = { song, transferState ->
                            if (transferState !is SongTransferState.Running) {
                                when {
                                    song.canBackup -> viewModel.uploadSong(song)
                                    song.canDownload -> runWithSongDirectory {
                                        viewModel.downloadSong(song)
                                    }
                                }
                            }
                        },
                        onShowDetails = { song -> detailsSong = song },
                        accentColor = accentNeonColor,
                        textWhite = textWhite,
                        textDim = textDim
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            }

        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF171A20)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryDestination.entries.forEach { destination ->
                val selected = primaryDestination == destination
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            onClick = { primaryDestination = destination },
                            role = Role.Tab
                        )
                        .testTag("primary_nav_${destination.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (useIconBottomNavigation) {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label,
                            tint = if (selected) accentNeonColor else textDim,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Text(
                            text = destination.label,
                            color = if (selected) accentNeonColor else textDim,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    detailsSong?.let { song ->
        LocalSongDetailsDialog(
            song = song,
            onDelete = { deleteLocalFile, removeFromCloudPlaylist ->
                viewModel.deleteSong(
                    song = song,
                    deleteLocalFile = deleteLocalFile,
                    removeFromCloudPlaylist = removeFromCloudPlaylist
                )
                detailsSong = null
            },
            onDismiss = { detailsSong = null },
            textWhite = textWhite,
            textDim = textDim
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

        if (primaryDestination != PrimaryDestination.Settings && statusMessageState != StatusMessageState.Idle) {
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
private fun LocalSongDetailsDialog(
    song: LibrarySongItem,
    onDelete: (deleteLocalFile: Boolean, removeFromCloudPlaylist: Boolean) -> Unit,
    onDismiss: () -> Unit,
    textWhite: Color,
    textDim: Color
) {
    val context = LocalContext.current
    val songStorage = remember(context) { com.hastur.hmusic.sync.SongStorage(context.applicationContext) }
    val localPath = song.localSong?.localPath
    val fileExists = remember(localPath) { songStorage.exists(localPath) }
    var deleteLocalFile by rememberSaveable(song.md5sum) { mutableStateOf(false) }
    var removeFromCloudPlaylist by rememberSaveable(song.md5sum) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("歌曲详细信息") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SongDetailRow("文件名称", songStorage.displayName(localPath) ?: song.fileName.ifBlank { "未知" }, textWhite, textDim)
                SongDetailRow("播放时长", formatDuration(song.durationMs), textWhite, textDim)
                SongDetailRow("文件大小", songStorage.size(localPath)?.let(::formatFileSize) ?: "文件不存在", textWhite, textDim)
                SongDetailRow("文件路径", songStorage.displayPath(localPath) ?: "无本地文件", textWhite, textDim)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = fileExists) {
                            deleteLocalFile = !deleteLocalFile
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteLocalFile,
                        onCheckedChange = { deleteLocalFile = it },
                        enabled = fileExists
                    )
                    Text("同时删除本地文件", color = textWhite)
                }

                if (song.remoteSong != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                removeFromCloudPlaylist = !removeFromCloudPlaylist
                            },
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = removeFromCloudPlaylist,
                            onCheckedChange = { removeFromCloudPlaylist = it }
                        )
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text("从云端歌单中移除", color = textWhite)
                            Text(
                                text = "只移除歌单记录，S3 中的音频文件会保留",
                                color = textDim,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDelete(deleteLocalFile, removeFromCloudPlaylist) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = Color(0xFF1A1D24),
        titleContentColor = textWhite,
        textContentColor = textDim
    )
}

@Composable
private fun SongDetailRow(
    label: String,
    value: String,
    textWhite: Color,
    textDim: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = textDim, fontSize = 12.sp)
        Text(
            text = value,
            color = textWhite,
            fontSize = 14.sp,
            fontFamily = if (label == "文件路径") FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = -1
    do {
        size /= 1024
        unitIndex++
    } while (size >= 1024 && unitIndex < units.lastIndex)
    return String.format(Locale.getDefault(), "%.2f %s", size, units[unitIndex])
}
