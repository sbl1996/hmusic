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

