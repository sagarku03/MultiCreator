package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.UserAgentMode
import com.example.data.WebContainerProfile
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassBadge
import com.example.ui.theme.GlassBorderMedium
import com.example.ui.theme.GlassIconButton
import com.example.ui.theme.GlassSurfaceFill
import com.example.ui.theme.GlassSurfaceHigh
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidPillButton
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBright
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianMid
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebStealthTab(
    containers: List<WebContainerProfile>,
    onLaunchContainer: (WebContainerProfile) -> Unit,
    onCreateContainer: (String, String, String, UserAgentMode, Boolean, Long) -> Unit,
    onRotateFingerprint: (WebContainerProfile) -> Unit,
    onPinShortcut: (WebContainerProfile) -> Unit,
    onDeleteContainer: (WebContainerProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showCreateSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "stealth_hero") {
            StealthSandboxHeroBanner(
                activeCount = containers.size,
                onCreateClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showCreateSheet = true
                }
            )
        }

        if (containers.isEmpty()) {
            item(key = "empty_containers") {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = NeonCyanBright,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = stringResource(R.string.empty_containers_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.empty_containers_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(items = containers, key = { it.id }) { container ->
                WebContainerCard(
                    container = container,
                    onLaunch = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLaunchContainer(container)
                    },
                    onRotate = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRotateFingerprint(container)
                    },
                    onPin = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPinShortcut(container)
                    },
                    onDelete = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDeleteContainer(container)
                    }
                )
            }
        }
    }

    if (showCreateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = sheetState,
            containerColor = ObsidianMid,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            CreateStealthContainerSheetContent(
                onDismiss = { showCreateSheet = false },
                onConfirm = { name, url, tag, uaMode, blockRtc, accentHex ->
                    onCreateContainer(name, url, tag, uaMode, blockRtc, accentHex)
                    showCreateSheet = false
                }
            )
        }
    }
}

@Composable
private fun StealthSandboxHeroBanner(
    activeCount: Int,
    onCreateClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = NeonCyan.copy(alpha = 0.30f), shape = shape)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_stealth_sandbox_hero_1795339133530),
            contentDescription = "Anti-Detect Web Stealth Sandbox Artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ObsidianDeep.copy(alpha = 0.94f),
                            ObsidianDeep.copy(alpha = 0.72f),
                            ObsidianDeep.copy(alpha = 0.30f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassBadge(
                    label = "$activeCount Isolated Jars",
                    color = EmeraldGlow,
                    pulsingDot = true
                )
                GlassBadge(
                    label = "Zero Hardware Track",
                    color = NeonCyanBright,
                    icon = Icons.Rounded.Shield
                )
            }
            Text(
                text = "Anti-Detect Web Stealth",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = "Run unlimited Instagram, YouTube, X, or TikTok logins with isolated cookie vaults, randomized User-Agents, and WebRTC leak blocking.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            LiquidPillButton(
                text = stringResource(R.string.action_new_container),
                icon = Icons.Rounded.Add,
                onClick = onCreateClick,
                modifier = Modifier.testTag("new_stealth_container_button")
            )
        }
    }
}

@Composable
private fun WebContainerCard(
    container: WebContainerProfile,
    onLaunch: () -> Unit,
    onRotate: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = Color(container.accentHex)
    val monogram = remember(container.name) {
        container.name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "MC" }
    }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("web_container_card_${container.id}"),
        highlightAccent = accentColor,
        onClick = onLaunch
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accentColor, CyberViolet)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = monogram,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = container.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = container.url.removePrefix("https://"),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                GlassIconButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${container.name}",
                    tint = RoseAlert,
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_container_${container.id}")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassBadge(
                    label = container.userAgentMode.badgeLabel,
                    color = NeonCyanBright,
                    icon = Icons.Rounded.Devices
                )
                if (container.blockWebRtc) {
                    GlassBadge(
                        label = stringResource(R.string.status_webrtc_blocked),
                        color = EmeraldGlow,
                        icon = Icons.Rounded.Shield
                    )
                }
                GlassBadge(
                    label = "ID ${container.fingerprintSeed.take(8)}",
                    color = CyberViolet
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidPillButton(
                    text = stringResource(R.string.action_launch_sandbox),
                    icon = Icons.Rounded.Launch,
                    onClick = onLaunch,
                    accentColors = listOf(accentColor, CyberViolet),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("launch_container_${container.id}")
                )
                GlassIconButton(
                    icon = Icons.Rounded.Autorenew,
                    contentDescription = stringResource(R.string.action_rotate_fingerprint),
                    tint = NeonCyanBright,
                    accentGlow = NeonCyan,
                    onClick = onRotate,
                    modifier = Modifier.testTag("rotate_fp_${container.id}")
                )
                GlassIconButton(
                    icon = Icons.Rounded.AddToHomeScreen,
                    contentDescription = stringResource(R.string.action_pin_home),
                    tint = EmeraldGlow,
                    accentGlow = EmeraldGlow,
                    onClick = onPin,
                    modifier = Modifier.testTag("pin_web_shortcut_${container.id}")
                )
            }
        }
    }
}

private data class PlatformPreset(
    val label: String,
    val defaultName: String,
    val url: String,
    val accentHex: Long,
    val uaMode: UserAgentMode
)

@Composable
private fun CreateStealthContainerSheetContent(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, UserAgentMode, Boolean, Long) -> Unit
) {
    val presets = remember {
        listOf(
            PlatformPreset("Instagram", "Instagram Stealth #5", "https://www.instagram.com/", 0xFFEC4899L, UserAgentMode.PIXEL_8_PRO),
            PlatformPreset("YouTube", "YouTube Creator #3", "https://studio.youtube.com/", 0xFFF43F5EL, UserAgentMode.DESKTOP_CHROME_MAC),
            PlatformPreset("X", "X Creator Alt", "https://x.com/home", 0xFF06B6D4L, UserAgentMode.RANDOM_ROTATE),
            PlatformPreset("TikTok", "TikTok Studio Alt", "https://www.tiktok.com/", 0xFF6366F1L, UserAgentMode.DESKTOP_CHROME_WIN),
            PlatformPreset("Custom", "Custom Vault", "https://", 0xFF10B981L, UserAgentMode.RANDOM_ROTATE)
        )
    }

    var name by remember { mutableStateOf("Instagram Creator #3") }
    var url by remember { mutableStateOf("https://www.instagram.com/") }
    var platformTag by remember { mutableStateOf("Instagram") }
    var selectedUa by remember { mutableStateOf(UserAgentMode.PIXEL_8_PRO) }
    var blockWebRtc by remember { mutableStateOf(true) }
    var accentHex by remember { mutableLongStateOf(0xFFEC4899L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Create Isolated Web Container",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Text(
            text = "Each container uses a separate cookie vault, hardware seed, and User-Agent profile.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        // Quick preset chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val active = platformTag == preset.label
                val chipShape = RoundedCornerShape(50.dp)
                Box(
                    modifier = Modifier
                        .clip(chipShape)
                        .background(
                            if (active) Color(preset.accentHex).copy(alpha = 0.25f) else GlassSurfaceFill
                        )
                        .border(
                            1.dp,
                            if (active) Color(preset.accentHex) else GlassBorderMedium,
                            chipShape
                        )
                        .clickable {
                            platformTag = preset.label
                            name = preset.defaultName
                            url = preset.url
                            accentHex = preset.accentHex
                            selectedUa = preset.uaMode
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) Color.White else TextSecondary
                    )
                }
            }
        }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonCyanBright,
            unfocusedBorderColor = GlassBorderMedium,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = NeonCyanBright,
            unfocusedLabelColor = TextSecondary,
            cursorColor = NeonCyanBright
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Container Name") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("container_name_input")
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Target HTTPS URL") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = NeonCyanBright
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            shape = RoundedCornerShape(18.dp),
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("container_url_input")
        )

        Text(
            text = "Spoofed User-Agent Profile",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UserAgentMode.entries.forEach { mode ->
                val selected = mode == selectedUa
                val chipShape = RoundedCornerShape(50.dp)
                Box(
                    modifier = Modifier
                        .clip(chipShape)
                        .background(if (selected) NeonCyan.copy(alpha = 0.25f) else GlassSurfaceHigh)
                        .border(1.dp, if (selected) NeonCyanBright else GlassBorderMedium, chipShape)
                        .clickable { selectedUa = mode }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = mode.badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else TextSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(GlassSurfaceFill)
                .border(1.dp, GlassBorderMedium, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Block WebRTC IP Leaks",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = "Neutralizes RTCPeerConnection local network probes",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = blockWebRtc,
                onCheckedChange = { blockWebRtc = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = EmeraldGlow
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidPillButton(
                text = stringResource(R.string.action_cancel),
                isPrimary = false,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            LiquidPillButton(
                text = stringResource(R.string.action_create),
                icon = Icons.Rounded.Add,
                onClick = {
                    onConfirm(name, url, platformTag, selectedUa, blockWebRtc, accentHex)
                },
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("confirm_create_container_button")
            )
        }
    }
}
