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
fun PlayerArtworkSection(
    modifier: Modifier = Modifier,
    currentSongTitle: String,
    currentSongArtist: String,
    currentSongLocalPath: String?,
    spinningAngle: Float,
    breathingScale: Float,
    isPlaylistExpanded: Boolean,
    accentColor: Color,
    textWhite: Color
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (isPlaylistExpanded) 12.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val artworkFrameSize = if (isPlaylistExpanded) {
            minOf(maxWidth * 0.62f, maxHeight * 0.68f, 240.dp)
        } else {
            minOf(maxWidth * 0.88f, maxHeight * 0.9f, 360.dp)
        }
        val backgroundGlowSize = artworkFrameSize * 0.83f
        val glassCardSize = artworkFrameSize * 0.825f
        val vinylSize = artworkFrameSize * 0.71f
        val artworkSize = artworkFrameSize * 0.46f

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isPlaylistExpanded) 10.dp else 14.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier.size(artworkFrameSize),
                contentAlignment = Alignment.Center
            ) {
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
                    Box(
                        modifier = Modifier
                            .size(vinylSize * breathingScale)
                            .rotate(spinningAngle),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = Color(0xFF131318), radius = size.width / 2.0f)
                            drawCircle(color = Color(0x22FFFFFF), radius = size.width / 2.2f, style = Stroke(1.0f))
                            drawCircle(color = Color(0x12909094), radius = size.width / 2.5f, style = Stroke(1.5f))
                            drawCircle(color = Color(0x12FFFFFF), radius = size.width / 3.0f, style = Stroke(1.5f))
                        }

                        ArtworkDisc(
                            filePath = currentSongLocalPath,
                            modifier = Modifier.size(artworkSize),
                            accentColor = accentColor,
                            placeholderIcon = Icons.Filled.MusicNote,
                            placeholderDescription = "Spinning disk icon"
                        )
                    }
                }
            }

            Text(
                text = currentSongTitle,
                color = textWhite,
                fontSize = if (isPlaylistExpanded) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = currentSongArtist,
                color = accentColor,
                fontSize = if (isPlaylistExpanded) 13.sp else 14.sp,
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
}
