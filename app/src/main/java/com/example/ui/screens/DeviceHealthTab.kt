package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import com.example.BuildConfig
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.DeviceHealthSnapshot
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassBadge
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassIconButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidPillButton
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBright
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DeviceHealthTab(
    diagnostics: DeviceHealthSnapshot?,
    clonedAppCount: Int,
    webContainerCount: Int,
    isSsaidRevealed: Boolean,
    isCheckingUpdates: Boolean,
    onToggleSsaid: () -> Unit,
    onProvisionWorkProfile: () -> Unit,
    onOpenGoogleAdsSettings: () -> Unit,
    onCopySsaid: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (diagnostics == null) {
            item(key = "loading_diag") {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Inspecting Android Work Profile & hardware isolation layer…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            return@LazyColumn
        }

        // Isolation Health Ring Hero Card
        item(key = "health_score_card") {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                highlightAccent = if (diagnostics.isWorkProfileActive) EmeraldGlow else NeonCyan
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    IsolationScoreRing(score = diagnostics.isolationScore)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GlassBadge(
                            label = when {
                                diagnostics.isProfileOwner -> stringResource(R.string.status_profile_owner)
                                diagnostics.isWorkProfileActive -> stringResource(R.string.status_profile_active)
                                else -> stringResource(R.string.status_profile_inactive)
                            },
                            color = if (diagnostics.isWorkProfileActive) EmeraldGlow else AmberPulse,
                            pulsingDot = diagnostics.isWorkProfileActive
                        )
                        Text(
                            text = "Anti-Ban Privacy Shield",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "$clonedAppCount Native Clones • $webContainerCount Stealth Jars • ${diagnostics.profileCount} User Profile(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // SSAID / Android ID Inspector Card
        item(key = "ssaid_card") {
            val formattedSsaid = if (isSsaidRevealed) {
                diagnostics.currentSsaid.uppercase()
            } else {
                val raw = diagnostics.currentSsaid
                if (raw.length > 6) "${raw.take(4)}••••••••${raw.takeLast(4)}".uppercase() else "••••••••"
            }

            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                highlightAccent = CyberViolet
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                tint = NeonCyanBright,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Active Profile SSAID",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassIconButton(
                                icon = if (isSsaidRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (isSsaidRevealed) "Mask SSAID" else "Reveal SSAID",
                                onClick = onToggleSsaid,
                                modifier = Modifier.testTag("toggle_ssaid_button")
                            )
                            GlassIconButton(
                                icon = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy SSAID",
                                tint = NeonCyanBright,
                                onClick = {
                                    clipboard.setText(AnnotatedString(diagnostics.currentSsaid))
                                    onCopySsaid("Copied active profile SSAID to clipboard.")
                                },
                                modifier = Modifier.testTag("copy_ssaid_button")
                            )
                        }
                    }

                    Text(
                        text = formattedSsaid,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NeonCyanBright
                    )
                    Text(
                        text = "Android generates a cryptographically scoped SSAID per signing key & user profile. Cloning inside a Work Profile assigns a fresh identity in /data/user/10/ without modifying APKs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Partition & Environment Diagnostics
        item(key = "partition_diagnostics") {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Sandbox & Partition Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    DiagnosticRow(
                        icon = Icons.Rounded.FolderSpecial,
                        label = "Active Partition",
                        value = diagnostics.currentStoragePartition,
                        valueColor = NeonCyanBright
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    DiagnosticRow(
                        icon = Icons.Rounded.AdminPanelSettings,
                        label = "Work Vault Partition",
                        value = diagnostics.expectedWorkPartition,
                        valueColor = if (diagnostics.isWorkProfileActive) EmeraldGlow else AmberPulse
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    DiagnosticRow(
                        icon = Icons.Rounded.VerifiedUser,
                        label = "Play Integrity Strategy",
                        value = "Official Managed Profile (Unmodified APKs)",
                        valueColor = EmeraldGlow
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    DiagnosticRow(
                        icon = Icons.Rounded.Security,
                        label = "Managed Users Support",
                        value = if (diagnostics.supportsManagedProfiles) "Hardware Supported" else "Unsupported on ROM",
                        valueColor = if (diagnostics.supportsManagedProfiles) EmeraldGlow else AmberPulse
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    DiagnosticRow(
                        icon = Icons.Rounded.Security,
                        label = "Hardware Security & TEE Attestation",
                        value = diagnostics.hardwareAttestationLevel,
                        valueColor = if (
                            diagnostics.hardwareAttestationLevel.contains("Verified") ||
                            diagnostics.hardwareAttestationLevel.contains("Active")
                        ) EmeraldGlow else AmberPulse
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    DiagnosticRow(
                        icon = Icons.Rounded.Memory,
                        label = "Device & OS",
                        value = "${diagnostics.deviceModel} • ${diagnostics.androidVersionLabel}",
                        valueColor = TextPrimary
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    DiagnosticRow(
                        icon = Icons.Rounded.Security,
                        label = "Chromium WebView Engine",
                        value = "${diagnostics.webViewEngineVersion} (${diagnostics.activeChromiumProfiles} Profiles)",
                        valueColor = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidPillButton(
                            text = "Reset Ad ID (AAID)",
                            icon = Icons.Rounded.Fingerprint,
                            isPrimary = false,
                            onClick = onOpenGoogleAdsSettings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reset_aaid_button")
                        )
                        if (!diagnostics.isWorkProfileActive) {
                            LiquidPillButton(
                                text = "Work Profile",
                                icon = Icons.Rounded.AdminPanelSettings,
                                onClick = onProvisionWorkProfile,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("health_provision_profile_button")
                            )
                        }
                    }
                }
            }
        }

        // Privacy Checklist Card
        item(key = "privacy_checklist") {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Anti-Detection Engine Audit",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    AuditCheckItem(
                        passed = diagnostics.multiProfileWebViewSupported,
                        title = "Native Chromium Multi-Profile (ProfileStore)",
                        subtitle = if (diagnostics.multiProfileWebViewSupported) {
                            "Every clone runs in a separate Chromium profile with isolated cookies, IndexedDB & renderer."
                        } else {
                            "Using encrypted private cookie-jar snapshotting fallback."
                        }
                    )
                    AuditCheckItem(
                        passed = diagnostics.documentStartShieldSupported,
                        title = "Pre-Execution document_start Fingerprint Shield",
                        subtitle = "Aligns User-Agent Client Hints, WebGL GPU, AudioContext & Canvas before website scripts run."
                    )
                    AuditCheckItem(
                        passed = diagnostics.requestedWithStripSupported,
                        title = "X-Requested-With WebView Header Stripping",
                        subtitle = "Removes host Android package name from outgoing WebView HTTP requests."
                    )
                    AuditCheckItem(
                        passed = true,
                        title = "WebRTC Local IP Leak Blocker",
                        subtitle = "Neutralizes RTCPeerConnection network probes inside every cloned container."
                    )
                    AuditCheckItem(
                        passed = diagnostics.isWorkProfileActive && diagnostics.isCrossProfileBridgeReady,
                        title = "Android Work Vault & Cross-Profile APK Bridge",
                        subtitle = when {
                            diagnostics.isProfileOwner -> "Running inside /data/user/10/ as active Profile Owner."
                            diagnostics.isWorkProfileActive && diagnostics.isCrossProfileBridgeReady ->
                                "Secondary OS partition (/data/user/10/) & cross-profile APK installer bridge active."
                            diagnostics.isWorkProfileActive ->
                                "Work Profile exists; open MultiCreator in Work tab once to sync the APK bridge."
                            else ->
                                "Tap Work Profile above or on Home to activate OS-level native cloning (/data/user/10/)."
                        }
                    )
                }
            }
        }

        // GitHub Releases OTA Auto-Update Card
        item(key = "ota_updater_card") {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                highlightAccent = NeonCyan
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GitHub Auto-Updater",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        GlassBadge(
                            label = "v${BuildConfig.VERSION_NAME} (#${BuildConfig.VERSION_CODE})",
                            color = NeonCyanBright
                        )
                    }
                    Text(
                        text = "Release Channel: ${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO} • Signed HTTPS APK Stream",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    LiquidPillButton(
                        text = if (isCheckingUpdates) "Checking GitHub Releases…" else stringResource(R.string.update_action_check),
                        icon = Icons.Rounded.SystemUpdateAlt,
                        enabled = !isCheckingUpdates,
                        onClick = onCheckForUpdates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_check_updates_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun IsolationScoreRing(score: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100) / 100f),
        animationSpec = tween(durationMillis = 850),
        label = "isolation_score_ring"
    )

    Box(
        modifier = Modifier.size(92.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 9.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(NeonCyan, CyberViolet, EmeraldGlow, NeonCyan)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score%",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(
                text = "SHIELD",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldGlow
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily
                ),
                color = valueColor
            )
        }
    }
}

@Composable
private fun AuditCheckItem(
    passed: Boolean,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (passed) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (passed) EmeraldGlow else AmberPulse,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
