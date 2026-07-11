package com.hastur.hmusic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hastur.hmusic.search.MusicdlSearchItem

@Composable
fun SearchSection(
    searchState: MusicdlSearchUiState,
    transferStates: Map<String, SongTransferState>,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDownload: (MusicdlSearchItem) -> Unit,
    accentColor: Color,
    textWhite: Color,
    textDim: Color,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val triggerSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onSearch()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchState.keyword,
                onValueChange = onKeywordChange,
                label = { Text("歌曲 / 歌手") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { triggerSearch() }),
                colors = searchTextFieldColors(accentColor, textWhite, textDim),
                modifier = Modifier
                    .weight(1f)
                    .testTag("musicdl_keyword_input")
            )
            IconButton(
                onClick = { if (!searchState.isSearching) triggerSearch() },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0x13FFFFFF), RoundedCornerShape(16.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            ) {
                if (searchState.isSearching) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                        trackColor = accentColor.copy(alpha = 0.18f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        searchState.error?.let { error ->
            Text(
                text = error,
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (searchState.items.isEmpty() && !searchState.isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "输入关键词搜索 musicdl 曲库",
                    color = textDim,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchState.items) { item ->
                    val transferKey = searchState.sessionId?.let { "musicdl:$it:${item.itemId}" }
                    val transferState = transferKey?.let { transferStates[it] } ?: SongTransferState.Idle
                    val isDownloaded = searchState.downloadedItemMd5ByKey.containsKey(musicdlItemStableKey(item))
                    SearchResultRow(
                        item = item,
                        isDownloaded = isDownloaded,
                        transferState = transferState,
                        onDownload = { onDownload(item) },
                        accentColor = accentColor,
                        textWhite = textWhite,
                        textDim = textDim
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: MusicdlSearchItem,
    isDownloaded: Boolean,
    transferState: SongTransferState,
    onDownload: () -> Unit,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    val isRunning = transferState is SongTransferState.Running
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isRunning) { onDownload() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0x09FFFFFF),
        border = BorderStroke(1.dp, Color(0x0AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (item.coverUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.songName ?: "未知歌曲",
                    color = textWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.singers ?: "佚名",
                    color = textDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SearchMetaChip(text = musicdlSourceDisplayName(item.source), accentColor = accentColor)
                    item.extension?.takeIf { it.isNotBlank() }?.let {
                        SearchMetaChip(text = it.uppercase(), accentColor = accentColor)
                    }
                    item.fileSize?.takeIf { it.isNotBlank() }?.let {
                        SearchMetaChip(text = it, accentColor = accentColor)
                    }
                    item.duration?.takeIf { it.isNotBlank() }?.let {
                        SearchMetaChip(text = it, accentColor = accentColor)
                    }
                    if (isDownloaded) {
                        SearchMetaChip(text = "已下载", accentColor = accentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when (transferState) {
                is SongTransferState.Running -> {
                    val progress = transferState.progress?.coerceIn(0f, 1f)
                    if (progress != null) {
                        CircularProgressIndicator(
                            progress = { progress },
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                            trackColor = accentColor.copy(alpha = 0.18f)
                        )
                    } else {
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                            trackColor = accentColor.copy(alpha = 0.18f)
                        )
                    }
                }

                is SongTransferState.Failed -> {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Download failed",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                SongTransferState.Idle -> {
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = "Download",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMetaChip(
    text: String,
    accentColor: Color
) {
    Text(
        text = text,
        color = accentColor.copy(alpha = 0.82f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(accentColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

private fun musicdlItemStableKey(item: MusicdlSearchItem): String {
    return listOf(
        item.source,
        item.songName,
        item.singers,
        item.fileSizeBytes?.toString() ?: item.fileSize,
        item.extension,
        item.durationSeconds?.toString() ?: item.duration
    ).joinToString("|") { it.orEmpty().trim().lowercase() }
}

private fun musicdlSourceDisplayName(source: String?): String {
    return when (source?.trim()) {
        "NeteaseMusicClient" -> "网易云"
        "QianqianMusicClient" -> "千千"
        "MiguMusicClient" -> "咪咕"
        "QQMusicClient" -> "QQ"
        "KuwoMusicClient" -> "酷我"
        "KugouMusicClient" -> "酷狗"
        null, "" -> "musicdl"
        else -> source.removeSuffix("MusicClient").removeSuffix("Client")
    }
}

@Composable
private fun searchTextFieldColors(
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = textWhite,
    unfocusedTextColor = textWhite,
    focusedBorderColor = accentColor,
    unfocusedBorderColor = Color(0x1EFFFFFF),
    focusedLabelColor = accentColor,
    unfocusedLabelColor = textDim,
    cursorColor = accentColor
)
