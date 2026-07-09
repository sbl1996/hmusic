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
fun OssSettingsCard(
    profiles: List<BackupProfileEntity>,
    activeProfile: BackupProfileEntity?,
    onSave: (String, String, String, Boolean, String, String, String, String) -> Unit,
    onCreateProfile: (Boolean) -> Unit,
    onSwitchProfile: (Long) -> Unit,
    onDeleteProfile: () -> Unit,
    onSync: () -> Unit,
    onShowStatusCompleted: (String) -> Unit,
    onShowStatusError: (String) -> Unit,
    accentColor: Color,
    textWhite: Color,
    textDim: Color
) {
    val context = LocalContext.current
    val clipboardPromptStore = remember(context) { ClipboardConfigPromptStore(context) }
    val coroutineScope = rememberCoroutineScope()
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteProfileConfirm by remember { mutableStateOf(false) }
    var autoFillClipboardCandidate by remember { mutableStateOf<ClipboardConfigCandidate?>(null) }
    var profileName by remember(activeProfile) { mutableStateOf(activeProfile?.name ?: "") }
    var endpoint by remember(activeProfile) { mutableStateOf(activeProfile?.endpoint ?: "") }
    var region by remember(activeProfile) { mutableStateOf(activeProfile?.region ?: "") }
    var forcePathStyle by remember(activeProfile) { mutableStateOf(activeProfile?.forcePathStyle ?: false) }
    var bucket by remember(activeProfile) { mutableStateOf(activeProfile?.bucket ?: "") }
    var ak by remember(activeProfile) { mutableStateOf(activeProfile?.accessKeyId ?: "") }
    var sk by remember(activeProfile) { mutableStateOf(activeProfile?.accessKeySecret ?: "") }
    var prefix by remember(activeProfile) { mutableStateOf(activeProfile?.prefix ?: "") }
    var skVisible by remember { mutableStateOf(false) }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }

    fun applyClipboardConfig(candidate: ClipboardConfigCandidate) {
        endpoint = candidate.parsed.endpoint ?: endpoint
        region = candidate.parsed.region ?: region
        forcePathStyle = candidate.parsed.forcePathStyle ?: forcePathStyle
        bucket = candidate.parsed.bucket ?: bucket
        ak = candidate.parsed.accessKeyId ?: ak
        sk = candidate.parsed.accessKeySecret ?: sk
        prefix = candidate.parsed.prefix ?: prefix
        if (profileName.isBlank()) {
            profileName = buildSuggestedProfileName(candidate.parsed)
        }
    }

    LaunchedEffect(Unit) {
        val candidate = readClipboardConfigCandidate(context) ?: return@LaunchedEffect
        val dismissedHash = clipboardPromptStore.readDismissedConfigHash()
        if (shouldSuggestClipboardConfig(candidate.hash, dismissedHash)) {
            autoFillClipboardCandidate = candidate
        }
    }

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

            Text(
                text = when {
                    activeProfile == null -> "尚未选择云端配置"
                    activeProfile.bucket.isBlank() -> "配置尚未完成"
                    else -> "${activeProfile.bucket} · ${activeProfile.endpoint}"
                },
                color = textDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onSync,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color(0xFF21005D)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "同步云端列表",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("立即同步", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedSettings = !showAdvancedSettings }
                    .testTag("cloud_advanced_settings_toggle"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0x0AFFFFFF),
                border = BorderStroke(1.dp, Color(0x14FFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = "编辑云端配置",
                        color = textWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showAdvancedSettings) {
                            Icons.Filled.ExpandLess
                        } else {
                            Icons.Filled.ExpandMore
                        },
                        contentDescription = if (showAdvancedSettings) "收起" else "展开",
                        tint = textDim
                    )
                }
            }

            AnimatedVisibility(visible = showAdvancedSettings) {
                Column {
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

            Button(
                onClick = { onSave(profileName, endpoint, region, forcePathStyle, bucket, ak, sk, prefix) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF21005D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_settings_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = "Save settings", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("保存配置", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        val rawText = clipboard?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        val clipboardText = decodeClipboardConfig(rawText)
                        if (clipboardText == null) {
                            onShowStatusError("剪贴板配置解密失败")
                            Toast.makeText(context, "剪贴板配置解密失败", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val candidate = buildClipboardConfigCandidate(clipboardText)
                        if (candidate == null) {
                            onShowStatusError("剪贴板里没有可解析的 S3 配置")
                            Toast.makeText(context, "剪贴板里没有可解析的 S3 配置", Toast.LENGTH_SHORT).show()
                        } else {
                            applyClipboardConfig(candidate)
                            val message = if (candidate.wasEncrypted) {
                                "已解密并从剪贴板填充配置"
                            } else {
                                "已从剪贴板填充配置"
                            }
                            onShowStatusCompleted(message)
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

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        val exportText = buildEnvClipboardConfig(
                            endpoint = endpoint,
                            region = region,
                            forcePathStyle = forcePathStyle,
                            bucket = bucket,
                            accessKeyId = ak,
                            accessKeySecret = sk,
                            prefix = prefix
                        )
                        val encryptedText = encryptClipboardConfig(exportText)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("S3 Config", encryptedText))
                        onShowStatusCompleted("加密配置已复制到剪贴板")
                        Toast.makeText(context, "加密配置已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF), contentColor = textWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy config", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制配置", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = showAdvancedSettings) {
                Column {
                    Button(
                        onClick = { showDeleteProfileConfirm = true },
                        enabled = profiles.size > 1,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33B3261E),
                            contentColor = Color(0xFFFFDAD6),
                            disabledContainerColor = Color(0x14FFFFFF),
                            disabledContentColor = textDim
                        ),
                        border = BorderStroke(
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
    }

    if (showDeleteProfileConfirm) {
        ConfirmationDialog(
            title = "确认删除配置",
            message = "确定要删除当前配置「$profileName」吗？此操作不可撤销。",
            confirmLabel = "删除",
            dismissLabel = "取消",
            onConfirm = {
                onDeleteProfile()
                showDeleteProfileConfirm = false
            },
            onDismiss = { showDeleteProfileConfirm = false }
        )
    }

    if (autoFillClipboardCandidate != null) {
        AlertDialog(
            onDismissRequest = {
                autoFillClipboardCandidate = null
            },
            title = { Text("检测到剪贴板配置") },
            text = { Text("是否从剪贴板解析并填写当前配置？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val candidate = autoFillClipboardCandidate ?: return@TextButton
                        applyClipboardConfig(candidate)
                        autoFillClipboardCandidate = null
                        val message = if (candidate.wasEncrypted) {
                            "已解密并从剪贴板填充配置"
                        } else {
                            "已从剪贴板填充配置"
                        }
                        onShowStatusCompleted(message)
                    }
                ) {
                    Text("是")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val candidate = autoFillClipboardCandidate ?: return@TextButton
                        autoFillClipboardCandidate = null
                        coroutineScope.launch {
                            clipboardPromptStore.saveDismissedConfigHash(candidate.hash)
                        }
                    }
                ) {
                    Text("否")
                }
            }
        )
    }
}
