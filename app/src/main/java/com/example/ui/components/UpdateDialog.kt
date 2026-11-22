package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.R
import com.example.engine.UpdateInfo
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassBadge
import com.example.ui.theme.GlassBorderMedium
import com.example.ui.theme.GlassSurfaceFill
import com.example.ui.theme.GlassSurfaceHigh
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidPillButton
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBright
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.util.Locale

sealed class UpdateDialogState {
    data object Hidden : UpdateDialogState()
    data class Available(val info: UpdateInfo) : UpdateDialogState()
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateDialogState()
    data class ReadyToInstall(
        val info: UpdateInfo,
        val apkFile: File,
        val needsPermission: Boolean = false
    ) : UpdateDialogState()
    data class Error(
        val info: UpdateInfo,
        val message: String
    ) : UpdateDialogState()
}

/**
 * Liquid Glassmorphism In-App Auto-Update Dialog supporting Available, Downloading,
 * Ready-to-Install (with automatic install dispatch), and Error states.
 */
@Composable
fun UpdateDialog(
    state: UpdateDialogState,
    onStartDownload: (UpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallApk: (File) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateDialogState.Hidden) return

    val info = when (state) {
        is UpdateDialogState.Available -> state.info
        is UpdateDialogState.Downloading -> state.info
        is UpdateDialogState.ReadyToInstall -> state.info
        is UpdateDialogState.Error -> state.info
        UpdateDialogState.Hidden -> return
    }

    // State 3: Auto-trigger APK installation as soon as download completes
    if (state is UpdateDialogState.ReadyToInstall && !state.needsPermission) {
        LaunchedEffect(state.apkFile.absolutePath) {
            onInstallApk(state.apkFile)
        }
    }

    val isDownloading = state is UpdateDialogState.Downloading
    val accentColor = when (state) {
        is UpdateDialogState.ReadyToInstall -> EmeraldGlow
        is UpdateDialogState.Error -> RoseAlert
        else -> NeonCyan
    }

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        val cardShape = RoundedCornerShape(24.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(cardShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianElevated.copy(alpha = 0.96f),
                            ObsidianDeep.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.65f),
                            GlassBorderMedium,
                            CyberViolet.copy(alpha = 0.55f)
                        )
                    ),
                    shape = cardShape
                )
                .padding(22.dp)
                .testTag("update_dialog_card")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Pulsing Luminous Update Icon
                PulsingUpdateHaloIcon(state = state, accentColor = accentColor)

                // Version Header & Metadata Badges
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "New Version Available: v${info.versionName}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassBadge(
                            label = "v${BuildConfig.VERSION_NAME} → v${info.versionName}",
                            color = NeonCyanBright
                        )
                        if (info.assetSizeBytes > 0L) {
                            GlassBadge(
                                label = formatBytes(info.assetSizeBytes),
                                color = CyberViolet
                            )
                        }
                    }
                }

                // Scrollable Release Notes / Changelog Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(GlassSurfaceSubtle)
                        .border(1.dp, GlassBorderMedium, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_changelog_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyanBright
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = 160.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = info.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Dynamic State Section & Action Buttons
                when (state) {
                    is UpdateDialogState.Available -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidPillButton(
                                text = stringResource(R.string.update_action_later),
                                isPrimary = false,
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("update_later_button")
                            )
                            LiquidPillButton(
                                text = stringResource(R.string.update_action_now),
                                icon = Icons.Rounded.SystemUpdateAlt,
                                onClick = { onStartDownload(info) },
                                modifier = Modifier
                                    .weight(1.35f)
                                    .testTag("update_now_button")
                            )
                        }
                    }

                    is UpdateDialogState.Downloading -> {
                        LiquidDownloadProgressSection(
                            progress = state.progress,
                            bytesDownloaded = state.bytesDownloaded,
                            totalBytes = if (state.totalBytes > 0L) state.totalBytes else info.assetSizeBytes
                        )
                        LiquidPillButton(
                            text = stringResource(R.string.action_cancel),
                            isPrimary = false,
                            onClick = onCancelDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_cancel_download_button")
                        )
                    }

                    is UpdateDialogState.ReadyToInstall -> {
                        Text(
                            text = if (state.needsPermission) {
                                "Enable \"Allow from this source\" in Android settings, then tap Install Now to complete the upgrade."
                            } else {
                                "Signed APK verified and ready. If the Android installer did not open automatically, tap Install Now below."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldGlow
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidPillButton(
                                text = stringResource(R.string.action_close),
                                isPrimary = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidPillButton(
                                text = if (state.needsPermission) {
                                    stringResource(R.string.update_action_allow_sources)
                                } else {
                                    stringResource(R.string.update_action_install)
                                },
                                icon = if (state.needsPermission) Icons.Rounded.Security else Icons.Rounded.InstallMobile,
                                accentColors = listOf(EmeraldGlow, NeonCyan),
                                onClick = { onInstallApk(state.apkFile) },
                                modifier = Modifier
                                    .weight(1.45f)
                                    .testTag("update_install_now_button")
                            )
                        }
                    }

                    is UpdateDialogState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = RoseAlert
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidPillButton(
                                text = stringResource(R.string.action_close),
                                isPrimary = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidPillButton(
                                text = stringResource(R.string.update_action_retry),
                                icon = Icons.Rounded.SystemUpdateAlt,
                                onClick = { onStartDownload(info) },
                                modifier = Modifier
                                    .weight(1.35f)
                                    .testTag("update_retry_button")
                            )
                        }
                    }

                    UpdateDialogState.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun PulsingUpdateHaloIcon(
    state: UpdateDialogState,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "update_halo")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    val icon = when (state) {
        is UpdateDialogState.Downloading -> Icons.Rounded.Downloading
        is UpdateDialogState.ReadyToInstall -> Icons.Rounded.CheckCircle
        is UpdateDialogState.Error -> Icons.Rounded.ErrorOutline
        else -> Icons.Rounded.SystemUpdateAlt
    }

    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = haloAlpha))
        )
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(accentColor, CyberViolet)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Software update status",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun LiquidDownloadProgressSection(
    progress: Float,
    bytesDownloaded: Long,
    totalBytes: Long
) {
    val animatedFraction by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180),
        label = "download_progress_bar"
    )
    val percentage = (animatedFraction * 100f).toInt().coerceIn(0, 100)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloading signed APK…",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = NeonCyanBright
            )
        }

        // Liquid gradient progress bar track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(GlassSurfaceHigh)
                .border(1.dp, GlassBorderMedium, RoundedCornerShape(50.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedFraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(NeonCyan, CyberViolet, EmeraldGlow)
                        )
                    )
            )
        }

        val bytesLabel = if (totalBytes > 0L) {
            "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)}"
        } else {
            "${formatBytes(bytesDownloaded)} downloaded"
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = bytesLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
