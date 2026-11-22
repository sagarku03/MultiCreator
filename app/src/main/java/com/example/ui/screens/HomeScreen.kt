package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
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
import com.example.engine.AppContainer
import com.example.engine.InstalledAppItem
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
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidPillButton
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBright
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RoseAlert
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
    onCreateAdditionalSlot: (InstalledAppItem) -> Unit,
    onLaunchSpecificClone: (AppContainer) -> Unit,
    onDeleteLocalClone: (InstalledAppItem) -> Unit,
    onPinAppShortcut: (InstalledAppItem) -> Unit,
    onLaunchWebContainer: (WebContainerProfile) -> Unit,
    onCreateWebContainer: (String, String, String, UserAgentMode, Boolean, Long) -> Unit,
    onRotateFingerprint: (WebContainerProfile) -> Unit,
    onPinWebShortcut: (WebContainerProfile) -> Unit,
    onDeleteWebContainer: (WebContainerProfile) -> Unit,
    onToggleSsaid: () -> Unit,
    onOpenGoogleAdsSettings: () -> Unit,
    isCheckingUpdates: Boolean,
    onCheckForUpdates: () -> Unit,
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
                    clonedCount = uiState.clonedCount,
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
                        MainTab.NATIVE_CLONER -> AppClonerTab(
                            uiState = uiState,
                            onSearchQueryChange = onSearchQueryChange,
                            onFilterModeChange = onFilterModeChange,
                            onCloneOrLaunchApp = { app ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCloneOrLaunchApp(app)
                            },
                            onCreateAdditionalSlot = { app ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCreateAdditionalSlot(app)
                            },
                            onLaunchSpecificClone = { container ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLaunchSpecificClone(container)
                            },
                            onDeleteLocalClone = { app ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteLocalClone(app)
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
                            isCheckingUpdates = isCheckingUpdates,
                            onToggleSsaid = onToggleSsaid,
                            onProvisionWorkProfile = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onProvisionWorkProfile()
                            },
                            onOpenGoogleAdsSettings = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenGoogleAdsSettings()
                            },
                            onCopySsaid = onPostMessage,
                            onCheckForUpdates = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCheckForUpdates()
                            }
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
    clonedCount: Int,
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
                    text = "1-Click Isolated App Cloner",
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
                label = if (clonedCount == 1) "1 Clone" else "$clonedCount Clones",
                color = if (clonedCount > 0) EmeraldGlow else NeonCyanBright,
                pulsingDot = clonedCount > 0
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
private fun AppClonerTab(
    uiState: MultiCreatorUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterModeChange: (AppFilterMode) -> Unit,
    onCloneOrLaunchApp: (InstalledAppItem) -> Unit,
    onCreateAdditionalSlot: (InstalledAppItem) -> Unit,
    onLaunchSpecificClone: (AppContainer) -> Unit,
    onDeleteLocalClone: (InstalledAppItem) -> Unit,
    onPinAppShortcut: (InstalledAppItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Frosted Search Bar right at the top
        item(key = "search_bar") {
            GlassSearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = stringResource(R.string.search_apps_placeholder)
            )
        }

        // Filter Chips Row: All Apps • Cloned Only • User Apps • System Apps
        item(key = "filter_chips") {
            AppFilterChipsRow(
                selectedMode = uiState.appFilterMode,
                totalCount = uiState.installedApps.size,
                clonedCount = uiState.clonedCount,
                onSelect = onFilterModeChange
            )
        }

        // Compact Cyber-Glass Vault Hero Banner
        item(key = "vault_hero") {
            ClonerHeroCard(
                clonedCount = uiState.clonedCount,
                totalCount = uiState.installedApps.size
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
                    val cloneSlots = uiState.clonesByPackageList[app.packageName].orEmpty()
                    InstalledAppGlassCard(
                        app = app,
                        cloneSlots = cloneSlots,
                        onCloneOrLaunch = { onCloneOrLaunchApp(app) },
                        onAddAnotherSlot = { onCreateAdditionalSlot(app) },
                        onLaunchSlot = onLaunchSpecificClone,
                        onDeleteClone = { onDeleteLocalClone(app) },
                        onPinShortcut = { onPinAppShortcut(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClonerHeroCard(
    clonedCount: Int,
    totalCount: Int
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
                        if (clonedCount > 0) EmeraldGlow.copy(alpha = 0.50f) else NeonCyan.copy(alpha = 0.45f),
                        CyberViolet.copy(alpha = 0.35f)
                    )
                ),
                shape = shape
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_cyber_vault_banner_1795339133969),
            contentDescription = "MultiCreator Isolated Container Banner",
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
                            ObsidianDeep.copy(alpha = 0.72f),
                            ObsidianDeep.copy(alpha = 0.25f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassBadge(
                    label = "$clonedCount Active Clones",
                    color = EmeraldGlow,
                    pulsingDot = clonedCount > 0
                )
                GlassBadge(
                    label = "$totalCount Installed",
                    color = CyberViolet,
                    icon = Icons.Rounded.Shield
                )
            }
            Text(
                text = "Instant Multi-Account Vault",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "Tap Clone App to create an isolated local container with a unique virtual SSAID and private cookie vault, then pin directly to your Home Screen.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
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
        Triple(AppFilterMode.ALL, "All Apps ($totalCount)", "filter_all"),
        Triple(AppFilterMode.CLONED, "Cloned Only ($clonedCount)", "filter_cloned"),
        Triple(AppFilterMode.USER, "User Apps", "filter_user"),
        Triple(AppFilterMode.SYSTEM, "System Apps", "filter_system")
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
    cloneSlots: List<AppContainer>,
    onCloneOrLaunch: () -> Unit,
    onAddAnotherSlot: () -> Unit,
    onLaunchSlot: (AppContainer) -> Unit,
    onDeleteClone: () -> Unit,
    onPinShortcut: () -> Unit
) {
    val latestClone = cloneSlots.lastOrNull()
    val isCloned = cloneSlots.isNotEmpty() || app.isClonedInWorkProfile

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_card_${app.packageName}"),
        highlightAccent = if (isCloned) EmeraldGlow else null,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconBadge(
                    drawable = app.icon,
                    appName = app.appName,
                    isCloned = isCloned
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
                        if (isCloned) {
                            GlassBadge(
                                label = if (cloneSlots.size > 1) {
                                    "${cloneSlots.size} Clones"
                                } else {
                                    stringResource(R.string.status_active_clone)
                                },
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
                    val metaLine = if (latestClone != null) {
                        "v${app.versionName} • SSAID ${latestClone.virtualSsaid.take(8)}"
                    } else {
                        "v${app.versionName} • ${if (app.isSystemApp) "System" else "User"}"
                    }
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (latestClone != null) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily
                        ),
                        color = if (latestClone != null) NeonCyanBright else TextSecondary.copy(alpha = 0.75f)
                    )
                }

                if (latestClone != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassIconButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = "Add another clone slot for ${app.appName}",
                            tint = NeonCyanBright,
                            accentGlow = NeonCyan,
                            onClick = onAddAnotherSlot,
                            modifier = Modifier.testTag("add_slot_${app.packageName}")
                        )
                        GlassIconButton(
                            icon = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete clone for ${app.appName}",
                            tint = RoseAlert,
                            onClick = onDeleteClone,
                            modifier = Modifier.testTag("delete_clone_${app.packageName}")
                        )
                    }
                }
            }

            // Multi-Slot Clone Switcher Chips (when 1+ isolated clones exist for this app)
            if (cloneSlots.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cloneSlots.forEach { slot ->
                        val chipShape = RoundedCornerShape(50.dp)
                        Box(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(EmeraldGlow.copy(alpha = 0.14f))
                                .border(1.dp, EmeraldGlow.copy(alpha = 0.45f), chipShape)
                                .clickable { onLaunchSlot(slot) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "#${slot.slotNumber} • ${slot.virtualSsaid.take(6)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = EmeraldGlow
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidPillButton(
                    text = if (isCloned) {
                        stringResource(R.string.action_launch)
                    } else {
                        stringResource(R.string.action_clone)
                    },
                    icon = if (isCloned) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.ContentCopy
                    },
                    accentColors = if (isCloned) {
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
