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
    isPlaylistExpanded: Boolean,
    onTogglePlaylist: () -> Unit,
    accentColor: Color,
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

        IconButton(
            onClick = { onTogglePlaylist() },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x13FFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                .testTag("playlist_toggle_button")
        ) {
            Icon(
                imageVector = if (isPlaylistExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = if (isPlaylistExpanded) "Collapse playlist" else "Expand playlist",
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

