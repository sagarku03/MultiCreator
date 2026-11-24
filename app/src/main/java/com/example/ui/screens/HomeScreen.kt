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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import android.os.Build
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.UserAgentMode
import com.example.data.WebContainerProfile
import com.example.engine.AppContainer
import com.example.engine.InstalledAppItem
import com.example.engine.ProvisioningPrecheckReport
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
    onForceProvisionWorkProfile: () -> Unit,
    onSyncWorkBridge: () -> Unit,
    onDismissWorkSetupDialog: () -> Unit,
    onDismissWorkVaultTroubleshooter: () -> Unit,
    onOpenAppsSettings: () -> Unit,
    onOpenAccountsSettings: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onCloneOrLaunchNativeApp: (InstalledAppItem) -> Unit,
    onOpenOrCreateWebClone: (InstalledAppItem) -> Unit,
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
                        MainTab.NATIVE_CLONER -> AppClonerTab(
                            uiState = uiState,
                            onSearchQueryChange = onSearchQueryChange,
                            onFilterModeChange = onFilterModeChange,
                            onProvisionWorkProfile = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onProvisionWorkProfile()
                            },
                            onSyncWorkBridge = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSyncWorkBridge()
                            },
                            onCloneOrLaunchNativeApp = { app ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCloneOrLaunchNativeApp(app)
                            },
                            onOpenOrCreateWebClone = { app ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenOrCreateWebClone(app)
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

        // 1-Tap Work Vault Activation Dialog when cloning a native app before Work Profile is active
        uiState.pendingWorkSetupApp?.let { targetApp ->
            WorkVaultSetupDialog(
                targetApp = targetApp,
                onActivateWorkVault = onProvisionWorkProfile,
                onUseWebClone = {
                    onDismissWorkSetupDialog()
                    onOpenOrCreateWebClone(targetApp)
                },
                onDismiss = onDismissWorkSetupDialog
            )
        }

        // OEM-Aware Live Work Vault Troubleshooter if Android blocked/cancelled Managed Profile creation
        if (uiState.showWorkVaultTroubleshooter) {
            WorkVaultTroubleshooterDialog(
                precheck = uiState.provisioningPrecheck,
                onOpenAppsSettings = onOpenAppsSettings,
                onOpenAccountsSettings = onOpenAccountsSettings,
                onOpenSecuritySettings = onOpenSecuritySettings,
                onRetrySetup = onForceProvisionWorkProfile,
                onUseWebStealthNow = {
                    onDismissWorkVaultTroubleshooter()
                    onSelectTab(MainTab.WEB_STEALTH)
                },
                onDismiss = onDismissWorkVaultTroubleshooter
            )
        }
    }
}

@Composable
private fun MultiCreatorTopBar(
    clonedCount: Int,
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
                    text = if (isProfileOwner) {
                        "Work Vault Mode (/data/user/10/)"
                    } else {
                        "Native Work Vault & Anti-Ban Cloner"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isProfileOwner) EmeraldGlow else TextSecondary
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
    onProvisionWorkProfile: () -> Unit,
    onSyncWorkBridge: () -> Unit,
    onCloneOrLaunchNativeApp: (InstalledAppItem) -> Unit,
    onOpenOrCreateWebClone: (InstalledAppItem) -> Unit,
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

        // Work Vault Status & 1-Tap Provisioning Hero Card
        item(key = "vault_hero") {
            ClonerHeroCard(
                isWorkProfileActive = uiState.isWorkProfileActive,
                isProfileOwner = uiState.isProfileOwner,
                isCrossProfileBridgeReady = uiState.isCrossProfileBridgeReady,
                isSelfInWorkProfile = uiState.isSelfInWorkProfile,
                nativeWorkClonedCount = uiState.nativeWorkClonedCount,
                webCloneCount = uiState.localClones.size,
                onProvisionWorkProfile = onProvisionWorkProfile,
                onSyncWorkBridge = onSyncWorkBridge
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
                        isCloningThisApp = uiState.cloningPackageName == app.packageName,
                        onCloneOrLaunchNative = { onCloneOrLaunchNativeApp(app) },
                        onOpenOrCreateWebClone = { onOpenOrCreateWebClone(app) },
                        onAddAnotherWebSlot = { onCreateAdditionalSlot(app) },
                        onLaunchWebSlot = onLaunchSpecificClone,
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
    isWorkProfileActive: Boolean,
    isProfileOwner: Boolean,
    isCrossProfileBridgeReady: Boolean,
    isSelfInWorkProfile: Boolean,
    nativeWorkClonedCount: Int,
    webCloneCount: Int,
    onProvisionWorkProfile: () -> Unit,
    onSyncWorkBridge: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val vaultReady = isProfileOwner || (isWorkProfileActive && isCrossProfileBridgeReady)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        if (vaultReady) EmeraldGlow.copy(alpha = 0.55f) else NeonCyan.copy(alpha = 0.50f),
                        CyberViolet.copy(alpha = 0.40f)
                    )
                ),
                shape = shape
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_cyber_vault_banner_1795339133969),
            contentDescription = "MultiCreator Work Vault Banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ObsidianDeep.copy(alpha = 0.95f),
                            ObsidianDeep.copy(alpha = 0.78f),
                            ObsidianDeep.copy(alpha = 0.35f)
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
                    label = when {
                        isProfileOwner -> "Work Vault (/data/user/10/)"
                        vaultReady -> "Work Vault Ready"
                        isWorkProfileActive && isSelfInWorkProfile -> "Bridge Sync Needed"
                        isWorkProfileActive -> "External Work Profile"
                        else -> "Setup Required for Native Clones"
                    },
                    color = when {
                        vaultReady -> EmeraldGlow
                        isWorkProfileActive -> NeonCyanBright
                        else -> AmberPulse
                    },
                    pulsingDot = vaultReady
                )
                if (nativeWorkClonedCount > 0 || webCloneCount > 0) {
                    GlassBadge(
                        label = "$nativeWorkClonedCount Native • $webCloneCount Web",
                        color = CyberViolet,
                        icon = Icons.Rounded.Shield
                    )
                }
            }
            Text(
                text = when {
                    isProfileOwner -> "Inside Isolated Work Profile"
                    vaultReady -> "Real OS-Level App Cloning Active"
                    else -> "Activate Android Work Vault"
                },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = when {
                    isProfileOwner -> "Every app shown here runs in /data/user/10/ with a separate SSAID & clean storage. Switch to Personal MultiCreator to clone more apps."
                    vaultReady -> "Tap Clone Native on any app to install a genuine 2nd copy into /data/user/10/ with a fresh SSAID (Play Integrity safe), or tap Web Clone for 3rd/4th accounts."
                    isWorkProfileActive && isSelfInWorkProfile -> "Work Profile detected! Tap Sync Bridge once so Personal MultiCreator can send unmodified APKs directly into /data/user/10/."
                    isWorkProfileActive -> "Another app already owns this phone's Work Profile slot. Use Web Clone for instant multi-accounts, or remove the existing Work Profile in Android Settings."
                    else -> "1-tap setup creates Android's official isolated Work Profile (/data/user/10/) with a brand-new SSAID & empty app data — zero root, zero factory reset."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (!isWorkProfileActive) {
                Spacer(modifier = Modifier.height(2.dp))
                LiquidPillButton(
                    text = "Activate Work Vault (1-Tap Setup)",
                    icon = Icons.Rounded.AdminPanelSettings,
                    accentColors = listOf(NeonCyan, CyberViolet),
                    onClick = onProvisionWorkProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_activate_work_vault_button")
                )
            } else if (!vaultReady && isSelfInWorkProfile) {
                Spacer(modifier = Modifier.height(2.dp))
                LiquidPillButton(
                    text = "Sync Work Vault Clone Bridge",
                    icon = Icons.Rounded.Sync,
                    accentColors = listOf(EmeraldGlow, NeonCyan),
                    onClick = onSyncWorkBridge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_sync_bridge_button")
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
    isCloningThisApp: Boolean,
    onCloneOrLaunchNative: () -> Unit,
    onOpenOrCreateWebClone: () -> Unit,
    onAddAnotherWebSlot: () -> Unit,
    onLaunchWebSlot: (AppContainer) -> Unit,
    onDeleteClone: () -> Unit,
    onPinShortcut: () -> Unit
) {
    val hasNativeClone = app.isClonedInWorkProfile
    val hasWebClones = cloneSlots.isNotEmpty()
    val isAnyCloned = hasNativeClone || hasWebClones

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_card_${app.packageName}"),
        highlightAccent = when {
            hasNativeClone -> EmeraldGlow
            hasWebClones -> NeonCyan
            else -> null
        },
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
                    isCloned = isAnyCloned
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hasNativeClone) {
                            GlassBadge(
                                label = "Work Clone",
                                color = EmeraldGlow,
                                pulsingDot = true
                            )
                        } else if (hasWebClones) {
                            GlassBadge(
                                label = "${cloneSlots.size} Web",
                                color = NeonCyanBright,
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
                    val metaLine = when {
                        hasNativeClone -> "v${app.versionName} • Isolated /data/user/10/ (Fresh SSAID)"
                        hasWebClones -> "v${app.versionName} • ${cloneSlots.size} Isolated Chromium Profile(s)"
                        else -> "v${app.versionName} • ${if (app.isSystemApp) "System" else "User"}"
                    }
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (isAnyCloned) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily
                        ),
                        color = when {
                            hasNativeClone -> EmeraldGlow
                            hasWebClones -> NeonCyanBright
                            else -> TextSecondary.copy(alpha = 0.75f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isAnyCloned) {
                    GlassIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = if (hasNativeClone) {
                            "Uninstall Work Clone of ${app.appName}"
                        } else {
                            "Delete Web Clone of ${app.appName}"
                        },
                        tint = RoseAlert,
                        onClick = onDeleteClone,
                        modifier = Modifier.testTag("delete_clone_${app.packageName}")
                    )
                }
            }

            // Multi-Slot Isolated Web Clone Chips (for 2nd/3rd/4th accounts via Chromium ProfileStore)
            if (hasWebClones) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cloneSlots.forEach { slot ->
                        val chipShape = RoundedCornerShape(50.dp)
                        Box(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(NeonCyan.copy(alpha = 0.14f))
                                .border(1.dp, NeonCyanBright.copy(alpha = 0.45f), chipShape)
                                .clickable { onLaunchWebSlot(slot) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Web #${slot.slotNumber} • ${slot.virtualSsaid.take(6)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = NeonCyanBright
                            )
                        }
                    }
                    if (app.hasWebPortal) {
                        val addShape = RoundedCornerShape(50.dp)
                        Row(
                            modifier = Modifier
                                .clip(addShape)
                                .background(CyberViolet.copy(alpha = 0.20f))
                                .border(1.dp, CyberViolet.copy(alpha = 0.55f), addShape)
                                .clickable { onAddAnotherWebSlot() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("add_slot_${app.packageName}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Web Clone Slot",
                                tint = TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Slot",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
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
                    text = when {
                        isCloningThisApp -> "Staging APK…"
                        hasNativeClone -> "Launch Work Clone"
                        else -> "Clone Native"
                    },
                    icon = if (hasNativeClone) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.ContentCopy
                    },
                    accentColors = if (hasNativeClone) {
                        listOf(EmeraldGlow, NeonCyan)
                    } else {
                        listOf(NeonCyan, CyberViolet)
                    },
                    enabled = !isCloningThisApp,
                    onClick = onCloneOrLaunchNative,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clone_button_${app.packageName}")
                )

                if (app.hasWebPortal || hasWebClones) {
                    LiquidPillButton(
                        text = if (hasWebClones) "Web" else "Web Clone",
                        icon = Icons.Rounded.Language,
                        isPrimary = false,
                        onClick = onOpenOrCreateWebClone,
                        modifier = Modifier.testTag("web_clone_button_${app.packageName}")
                    )
                }

                GlassIconButton(
                    icon = Icons.Rounded.AddToHomeScreen,
                    contentDescription = "Add ${app.appName} Clone to Home Screen",
                    tint = if (isAnyCloned) EmeraldGlow else TextSecondary,
                    accentGlow = if (isAnyCloned) EmeraldGlow else null,
                    onClick = onPinShortcut,
                    modifier = Modifier.testTag("pin_button_${app.packageName}")
                )
            }
        }
    }
}

@Composable
private fun WorkVaultSetupDialog(
    targetApp: InstalledAppItem,
    onActivateWorkVault: () -> Unit,
    onUseWebClone: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                highlightAccent = NeonCyan,
                contentPadding = PaddingValues(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = NeonCyanBright,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Activate Real App Cloning",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "To install a real 2nd copy of ${targetApp.appName} (instead of opening your old personal app), Android needs its official isolated Work Vault (/data/user/10/).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    Text(
                        text = "• Fresh SSAID & separate clean storage partition\n• Unmodified original APK (Play Integrity safe)\n• Personal data & apps stay 100% untouched (no factory reset)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LiquidPillButton(
                        text = "Activate Work Vault Now (1-Tap)",
                        icon = Icons.Rounded.AdminPanelSettings,
                        accentColors = listOf(NeonCyan, CyberViolet),
                        onClick = onActivateWorkVault,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_activate_work_vault")
                    )

                    if (targetApp.hasWebPortal) {
                        LiquidPillButton(
                            text = "Or Open Instant Web Clone (No Setup)",
                            icon = Icons.Rounded.Language,
                            isPrimary = false,
                            onClick = onUseWebClone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_use_web_clone")
                        )
                    }

                    LiquidPillButton(
                        text = "Cancel",
                        isPrimary = false,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkVaultTroubleshooterDialog(
    precheck: ProvisioningPrecheckReport?,
    onOpenAppsSettings: () -> Unit,
    onOpenAccountsSettings: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onRetrySetup: () -> Unit,
    onUseWebStealthNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val manufacturer = remember { Build.MANUFACTURER.lowercase() }
    val headline = precheck?.blockerHeadline ?: "Work Vault Setup Blocked by OS"
    val detailText = precheck?.blockerDetail ?: when {
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
            "Vivo / iQOO (Funtouch OS) blocked ManagedProvisioning:\n1) Turn OFF built-in App Clone (Settings → Apps → App Clone) and restart.\n2) Temporarily set Screen Lock to None/Swipe and turn off Play Protect scanning during setup.\n3) Remove any old Work Profile under Settings → Accounts."
        else ->
            "Android allows only 1 Managed Work Profile per device. Remove any existing Work Profile or built-in Dual Apps / App Clone, then retry."
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                highlightAccent = AmberPulse,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = AmberPulse,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    // Live Android System Diagnostics Box
                    if (precheck != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianElevated.copy(alpha = 0.85f))
                                .border(1.dp, GlassBorderMedium, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "LIVE DEVICE DIAGNOSTIC",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyanBright
                            )
                            Text(
                                text = "OS Allow Managed Profile: ${if (precheck.isProvisioningAllowedByOs) "YES" else "BLOCKED (false)"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (precheck.isProvisioningAllowedByOs) EmeraldGlow else RoseAlert
                            )
                            Text(
                                text = "Profiles: ${precheck.detectedProfileSummary}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = TextPrimary
                            )
                            if (precheck.oemCloneApps.isNotEmpty()) {
                                Text(
                                    text = "User 999 Clones: ${precheck.oemCloneApps.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = AmberPulse
                                )
                            }
                        }
                    }

                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )

                    LiquidPillButton(
                        text = "1. Open Apps / App Clone Settings",
                        isPrimary = false,
                        onClick = onOpenAppsSettings,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidPillButton(
                            text = "2. Accounts",
                            isPrimary = false,
                            onClick = onOpenAccountsSettings,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidPillButton(
                            text = "3. Security",
                            isPrimary = false,
                            onClick = onOpenSecuritySettings,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LiquidPillButton(
                        text = "Launch Work Vault Wizard Anyway",
                        icon = Icons.Rounded.AdminPanelSettings,
                        accentColors = listOf(NeonCyan, CyberViolet),
                        onClick = onRetrySetup,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LiquidPillButton(
                        text = "Use Instant Web Clones (Works Now)",
                        icon = Icons.Rounded.Language,
                        accentColors = listOf(EmeraldGlow, NeonCyan),
                        onClick = onUseWebStealthNow,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LiquidPillButton(
                        text = "Close",
                        isPrimary = false,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
