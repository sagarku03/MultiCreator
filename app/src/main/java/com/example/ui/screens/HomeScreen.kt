package com.example.ui.screens

import androidx.activity.compose.BackHandler
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.UserAgentMode
import com.example.data.WebContainerProfile
import com.example.engine.InstalledAppItem
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberGlassBackground
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.FloatingGlassNavBar
import com.example.ui.theme.GlassBadge
import com.example.ui.theme.GlassBorderMedium
import com.example.ui.theme.GlassIconButton
import com.example.ui.theme.GlassNavItem
import com.example.ui.theme.GlassSearchBar
import com.example.ui.theme.GlassSurfaceFill
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidPillButton
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBright
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppFilterMode
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MultiCreatorUiState

@Composable
fun HomeScreen(
    uiState: MultiCreatorUiState,
    onSelectTab: (MainTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterModeChange: (AppFilterMode) -> Unit,
    onRefresh: () -> Unit,
    onProvisionWorkProfile: () -> Unit,
    onCloneOrLaunchApp: (InstalledAppItem) -> Unit,
    onPinAppShortcut: (InstalledAppItem) -> Unit,
    onLaunchWebContainer: (WebContainerProfile) -> Unit,
    onCreateWebContainer: (String, String, String, UserAgentMode, Boolean, Long) -> Unit,
    onRotateFingerprint: (WebContainerProfile) -> Unit,
    onPinWebShortcut: (WebContainerProfile) -> Unit,
    onDeleteWebContainer: (WebContainerProfile) -> Unit,
    onToggleSsaid: () -> Unit,
    onPostMessage: (String) -> Unit,
    onConsumeMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = uiState.selectedTab != MainTab.NATIVE_CLONER) {
        onSelectTab(MainTab.NATIVE_CLONER)
    }

    LaunchedEffect(uiState.notificationMessage) {
        val message = uiState.notificationMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onConsumeMessage()
        }
    }

    val navItems = listOf(
        GlassNavItem(
            id = MainTab.NATIVE_CLONER.routeId,
            label = stringResource(R.string.tab_native_cloner),
            selectedIcon = Icons.Rounded.Apps,
            unselectedIcon = Icons.Outlined.Apps,
            testTag = "nav_tab_native_cloner"
        ),
        GlassNavItem(
            id = MainTab.WEB_STEALTH.routeId,
            label = stringResource(R.string.tab_web_stealth),
            selectedIcon = Icons.Rounded.Public,
            unselectedIcon = Icons.Outlined.Public,
            testTag = "nav_tab_web_stealth"
        ),
        GlassNavItem(
            id = MainTab.DEVICE_HEALTH.routeId,
            label = stringResource(R.string.tab_device_health),
            selectedIcon = Icons.Rounded.HealthAndSafety,
            unselectedIcon = Icons.Outlined.HealthAndSafety,
            testTag = "nav_tab_device_health"
        )
    )

    CyberGlassBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .statusBarsPadding()
            ) {
                MultiCreatorTopBar(
                    isWorkProfileActive = uiState.isWorkProfileActive,
                    isProfileOwner = uiState.isProfileOwner,
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefresh()
                    }
                )

                AnimatedContent(
                    targetState = uiState.selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.weight(1f),
                    label = "main_tab_transition"
                ) { tab ->
                    when (tab) {
                        MainTab.NATIVE_CLONER -> NativeClonerTab(
                            uiState = uiState,
                            onSearchQueryChange = onSearchQueryChange,
                            onFilterModeChange = onFilterModeChange,
                            onProvisionWorkProfile = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onProvisionWorkProfile()
                            },
                            onCloneOrLaunchApp = { app ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCloneOrLaunchApp(app)
                            },
                            onPinAppShortcut = { app ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPinAppShortcut(app)
                            }
                        )

                        MainTab.WEB_STEALTH -> WebStealthTab(
                            containers = uiState.webContainers,
                            onLaunchContainer = onLaunchWebContainer,
                            onCreateContainer = onCreateWebContainer,
                            onRotateFingerprint = onRotateFingerprint,
                            onPinShortcut = onPinWebShortcut,
                            onDeleteContainer = onDeleteWebContainer
                        )

                        MainTab.DEVICE_HEALTH -> DeviceHealthTab(
                            diagnostics = uiState.diagnostics,
                            clonedAppCount = uiState.clonedCount,
                            webContainerCount = uiState.webContainers.size,
                            isSsaidRevealed = uiState.isSsaidRevealed,
                            onToggleSsaid = onToggleSsaid,
                            onProvisionWorkProfile = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onProvisionWorkProfile()
                            },
                            onCopySsaid = onPostMessage
                        )
                    }
                }
            }

            // Floating Glass Snackbar above bottom pill nav
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp)
                    .padding(bottom = 104.dp, start = 16.dp, end = 16.dp)
            ) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = ObsidianElevated,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            // Floating Liquid Glass Pill Bottom Nav Bar
            FloatingGlassNavBar(
                items = navItems,
                selectedId = uiState.selectedTab.routeId,
                onSelect = { routeId ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTab(MainTab.fromRouteId(routeId))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 560.dp)
            )
        }
    }
}

@Composable
private fun MultiCreatorTopBar(
    isWorkProfileActive: Boolean,
    isProfileOwner: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(NeonCyanBright, CyberViolet)),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_multicreator_art_1795339133136),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassBadge(
                label = when {
                    isProfileOwner -> "Vault Owner"
                    isWorkProfileActive -> "Vault Active"
                    else -> "Standby"
                },
                color = if (isWorkProfileActive) EmeraldGlow else AmberPulse,
                pulsingDot = isWorkProfileActive
            )
            GlassIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.action_refresh),
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_apps_button")
            )
        }
    }
}

@Composable
private fun NativeClonerTab(
    uiState: MultiCreatorUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterModeChange: (AppFilterMode) -> Unit,
    onProvisionWorkProfile: () -> Unit,
    onCloneOrLaunchApp: (InstalledAppItem) -> Unit,
    onPinAppShortcut: (InstalledAppItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Vault Banner
        item(key = "vault_hero") {
            WorkProfileHeroCard(
                isWorkProfileActive = uiState.isWorkProfileActive,
                isProfileOwner = uiState.isProfileOwner,
                clonedCount = uiState.clonedCount,
                totalCount = uiState.installedApps.size,
                onProvisionClick = onProvisionWorkProfile
            )
        }

        // Frosted Search Input
        item(key = "search_bar") {
            GlassSearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = stringResource(R.string.search_apps_placeholder)
            )
        }

        // Filter Chips Row
        item(key = "filter_chips") {
            AppFilterChipsRow(
                selectedMode = uiState.appFilterMode,
                totalCount = uiState.installedApps.size,
                clonedCount = uiState.clonedCount,
                onSelect = onFilterModeChange
            )
        }

        // Loading, Empty, or Installed App Cards
        when {
            uiState.isLoadingApps -> {
                item(key = "loading_apps") {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = NeonCyanBright,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = stringResource(R.string.loading_apps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            uiState.filteredApps.isEmpty() -> {
                item(key = "empty_apps") {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(28.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = NeonCyanBright,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_apps_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.empty_apps_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            else -> {
                items(
                    items = uiState.filteredApps,
                    key = { it.packageName }
                ) { app ->
                    InstalledAppGlassCard(
                        app = app,
                        onCloneOrLaunch = { onCloneOrLaunchApp(app) },
                        onPinShortcut = { onPinAppShortcut(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkProfileHeroCard(
    isWorkProfileActive: Boolean,
    isProfileOwner: Boolean,
    clonedCount: Int,
    totalCount: Int,
    onProvisionClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        if (isWorkProfileActive) EmeraldGlow.copy(alpha = 0.55f) else NeonCyan.copy(alpha = 0.45f),
                        CyberViolet.copy(alpha = 0.35f)
                    )
                ),
                shape = shape
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_cyber_vault_banner_1795339133969),
            contentDescription = "MultiCreator Work Profile Vault Banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ObsidianDeep.copy(alpha = 0.94f),
                            ObsidianDeep.copy(alpha = 0.74f),
                            ObsidianDeep.copy(alpha = 0.25f)
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
                    label = if (isWorkProfileActive) "/data/user/10/ Ready" else "Fresh SSAID Vault",
                    color = if (isWorkProfileActive) EmeraldGlow else NeonCyanBright,
                    pulsingDot = isWorkProfileActive
                )
                GlassBadge(
                    label = "$clonedCount / $totalCount Apps",
                    color = CyberViolet,
                    icon = Icons.Rounded.Shield
                )
            }

            Text(
                text = if (isProfileOwner) {
                    "Isolated Work Vault Active"
                } else if (isWorkProfileActive) {
                    "Work Profile Partition Detected"
                } else {
                    "Native Work Profile Cloner"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )

            Text(
                text = if (isWorkProfileActive) {
                    "Clone apps into your isolated Android Work Profile to preserve Play Integrity and stop Instagram/YouTube device-ban loops."
                } else {
                    "Provision an official Android Managed Profile with a fresh SSAID and isolated /data/user/10/ storage partition."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            if (!isWorkProfileActive) {
                Spacer(modifier = Modifier.height(2.dp))
                LiquidPillButton(
                    text = stringResource(R.string.action_provision_profile),
                    icon = Icons.Rounded.AdminPanelSettings,
                    onClick = onProvisionClick,
                    modifier = Modifier.testTag("provision_work_profile_button")
                )
            }
        }
    }
}

@Composable
private fun AppFilterChipsRow(
    selectedMode: AppFilterMode,
    totalCount: Int,
    clonedCount: Int,
    onSelect: (AppFilterMode) -> Unit
) {
    val chips = listOf(
        Triple(AppFilterMode.ALL, "${stringResource(R.string.filter_all)} ($totalCount)", "filter_all"),
        Triple(AppFilterMode.CLONED, "${stringResource(R.string.filter_cloned)} ($clonedCount)", "filter_cloned"),
        Triple(AppFilterMode.USER, stringResource(R.string.filter_user), "filter_user"),
        Triple(AppFilterMode.SYSTEM, stringResource(R.string.filter_system), "filter_system")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (mode, label, tag) ->
            val selected = mode == selectedMode
            val shape = RoundedCornerShape(50.dp)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(
                                listOf(NeonCyan.copy(alpha = 0.30f), CyberViolet.copy(alpha = 0.30f))
                            )
                        } else {
                            Brush.linearGradient(listOf(GlassSurfaceFill, GlassSurfaceFill))
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) NeonCyanBright else GlassBorderMedium,
                        shape = shape
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag(tag)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun InstalledAppGlassCard(
    app: InstalledAppItem,
    onCloneOrLaunch: () -> Unit,
    onPinShortcut: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_card_${app.packageName}"),
        highlightAccent = if (app.isClonedInWorkProfile) EmeraldGlow else null,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconBadge(
                    drawable = app.icon,
                    appName = app.appName,
                    isCloned = app.isClonedInWorkProfile
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (app.isClonedInWorkProfile) {
                            GlassBadge(
                                label = stringResource(R.string.status_cloned),
                                color = EmeraldGlow,
                                pulsingDot = true
                            )
                        }
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v${app.versionName} • ${if (app.isSystemApp) "System" else "User"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.75f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidPillButton(
                    text = if (app.isClonedInWorkProfile) {
                        stringResource(R.string.action_launch)
                    } else {
                        stringResource(R.string.action_clone)
                    },
                    icon = if (app.isClonedInWorkProfile) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.ContentCopy
                    },
                    accentColors = if (app.isClonedInWorkProfile) {
                        listOf(EmeraldGlow, NeonCyan)
                    } else {
                        listOf(NeonCyan, CyberViolet)
                    },
                    onClick = onCloneOrLaunch,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clone_button_${app.packageName}")
                )

                LiquidPillButton(
                    text = stringResource(R.string.action_pin_home),
                    icon = Icons.Rounded.AddToHomeScreen,
                    isPrimary = false,
                    onClick = onPinShortcut,
                    modifier = Modifier.testTag("pin_button_${app.packageName}")
                )
            }
        }
    }
}

@Composable
private fun AppIconBadge(
    drawable: Drawable?,
    appName: String,
    isCloned: Boolean
) {
    val bitmap = remember(drawable) {
        drawable?.let { runCatching { it.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull() }
    }

    Box(modifier = Modifier.size(50.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$appName icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(15.dp))
                    .border(
                        width = 1.dp,
                        color = if (isCloned) EmeraldGlow.copy(alpha = 0.6f) else GlassBorderMedium,
                        shape = RoundedCornerShape(15.dp)
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(NeonCyan, CyberViolet))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appName.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        if (isCloned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(ObsidianDeep)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(EmeraldGlow)
            )
        }
    }
}
