package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ContainerStore
import com.example.data.UserAgentMode
import com.example.data.WebContainerProfile
import com.example.engine.AppContainer
import com.example.engine.CloneActionResult
import com.example.engine.ContainerEngine
import com.example.engine.DeviceHealthSnapshot
import com.example.engine.InstalledAppItem
import com.example.engine.ShortcutCreator
import com.example.engine.ShortcutPinResult
import com.example.engine.WebSandboxManager
import com.example.engine.WorkProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val routeId: String) {
    NATIVE_CLONER("native_cloner"),
    WEB_STEALTH("web_stealth"),
    DEVICE_HEALTH("device_health");

    companion object {
        fun fromRouteId(id: String): MainTab =
            entries.firstOrNull { it.routeId == id } ?: NATIVE_CLONER
    }
}

enum class AppFilterMode {
    ALL,
    CLONED,
    USER,
    SYSTEM
}

data class MultiCreatorUiState(
    val selectedTab: MainTab = MainTab.NATIVE_CLONER,
    val isLoadingApps: Boolean = true,
    val installedApps: List<InstalledAppItem> = emptyList(),
    val filteredApps: List<InstalledAppItem> = emptyList(),
    val localClones: List<AppContainer> = emptyList(),
    val clonesByPackage: Map<String, AppContainer> = emptyMap(),
    val clonesByPackageList: Map<String, List<AppContainer>> = emptyMap(),
    val searchQuery: String = "",
    val appFilterMode: AppFilterMode = AppFilterMode.ALL,
    val isWorkProfileActive: Boolean = false,
    val isProfileOwner: Boolean = false,
    val isCrossProfileBridgeReady: Boolean = false,
    val isSelfInWorkProfile: Boolean = false,
    val supportsManagedProfiles: Boolean = true,
    val cloningPackageName: String? = null,
    val pendingWorkSetupApp: InstalledAppItem? = null,
    val showWorkVaultTroubleshooter: Boolean = false,
    val nativeWorkClonedCount: Int = 0,
    val clonedCount: Int = 0,
    val webContainers: List<WebContainerProfile> = emptyList(),
    val diagnostics: DeviceHealthSnapshot? = null,
    val isSsaidRevealed: Boolean = false,
    val notificationMessage: String? = null
)

sealed class MultiCreatorEvent {
    data class LaunchWorkProfileProvisioning(val intent: Intent) : MultiCreatorEvent()
    data class LaunchCrossProfileIntent(val intent: Intent) : MultiCreatorEvent()
    data class LaunchContainer(val container: AppContainer) : MultiCreatorEvent()
    data class OpenWebSandbox(val container: WebContainerProfile) : MultiCreatorEvent()
}

class MultiCreatorViewModel(
    private val containerEngine: ContainerEngine,
    private val workProfileManager: WorkProfileManager,
    private val shortcutCreator: ShortcutCreator,
    private val containerStore: ContainerStore,
    private val webSandboxManager: WebSandboxManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiCreatorUiState())
    val uiState: StateFlow<MultiCreatorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MultiCreatorEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MultiCreatorEvent> = _events.asSharedFlow()

    init {
        observeLocalClones()
        observeWebContainers()
        refreshEcosystem()
    }

    private fun observeLocalClones() {
        viewModelScope.launch {
            containerEngine.containersFlow.collect { clones ->
                val grouped = clones
                    .groupBy { it.packageName }
                    .mapValues { (_, list) -> list.sortedBy { it.slotNumber } }
                val primaryByPackage = grouped.mapValues { (_, list) -> list.last() }
                _uiState.update { state ->
                    val mergedApps = sortAppsWithClones(state.installedApps, primaryByPackage)
                    val filtered = applyAppFilter(mergedApps, primaryByPackage, state.searchQuery, state.appFilterMode)
                    val workCount = mergedApps.count { it.isClonedInWorkProfile }
                    state.copy(
                        localClones = clones,
                        clonesByPackage = primaryByPackage,
                        clonesByPackageList = grouped,
                        installedApps = mergedApps,
                        filteredApps = filtered,
                        nativeWorkClonedCount = workCount,
                        clonedCount = workCount + clones.size
                    )
                }
            }
        }
    }

    private fun observeWebContainers() {
        viewModelScope.launch {
            containerStore.containersFlow.collect { containers ->
                _uiState.update { state ->
                    state.copy(webContainers = containers)
                }
                refreshDiagnosticsOnly(containers.size)
            }
        }
    }

    fun refreshEcosystem() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = it.installedApps.isEmpty()) }
            val result = withContext(Dispatchers.IO) {
                val workActive = workProfileManager.checkIfWorkProfileActive()
                val profileOwner = workProfileManager.isProfileOwner()
                val bridgeReady = workProfileManager.isCrossProfileBridgeReady()
                val selfInWork = workProfileManager.isSelfInstalledInWorkProfile()
                val supportsManaged = workProfileManager.isManagedProfileSupported()
                val apps = workProfileManager.getInstalledApps()
                val diag = workProfileManager.getDeviceDiagnostics(_uiState.value.webContainers.size)
                EcosystemRefreshSnapshot(
                    workActive = workActive,
                    profileOwner = profileOwner,
                    bridgeReady = bridgeReady,
                    selfInWork = selfInWork,
                    supportsManaged = supportsManaged,
                    apps = apps,
                    diag = diag
                )
            }
            _uiState.update { state ->
                val byPackage = state.clonesByPackage
                val sorted = sortAppsWithClones(result.apps, byPackage)
                val filtered = applyAppFilter(sorted, byPackage, state.searchQuery, state.appFilterMode)
                val workCount = sorted.count { it.isClonedInWorkProfile }
                state.copy(
                    isLoadingApps = false,
                    installedApps = sorted,
                    filteredApps = filtered,
                    isWorkProfileActive = result.workActive,
                    isProfileOwner = result.profileOwner,
                    isCrossProfileBridgeReady = result.bridgeReady,
                    isSelfInWorkProfile = result.selfInWork,
                    supportsManagedProfiles = result.supportsManaged,
                    nativeWorkClonedCount = workCount,
                    clonedCount = workCount + state.localClones.size,
                    diagnostics = result.diag
                )
            }
        }
    }

    private fun refreshDiagnosticsOnly(containerCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val diag = workProfileManager.getDeviceDiagnostics(containerCount)
            _uiState.update { it.copy(diagnostics = diag) }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredApps = applyAppFilter(
                    state.installedApps,
                    state.clonesByPackage,
                    query,
                    state.appFilterMode
                )
            )
        }
    }

    fun onFilterModeChange(mode: AppFilterMode) {
        _uiState.update { state ->
            state.copy(
                appFilterMode = mode,
                filteredApps = applyAppFilter(
                    state.installedApps,
                    state.clonesByPackage,
                    state.searchQuery,
                    mode
                )
            )
        }
    }

    fun toggleSsaidVisibility() {
        _uiState.update { it.copy(isSsaidRevealed = !it.isSsaidRevealed) }
    }

    fun dismissWorkSetupDialog() {
        _uiState.update { it.copy(pendingWorkSetupApp = null) }
    }

    fun dismissWorkVaultTroubleshooter() {
        _uiState.update { it.copy(showWorkVaultTroubleshooter = false) }
    }

    fun openAndroidAccountsSettings() {
        if (!workProfileManager.openAndroidAccountsSettings()) {
            postMessage("Open Android Settings → Passwords & accounts to inspect Work Profiles.")
        }
    }

    fun openAndroidSecuritySettings() {
        if (!workProfileManager.openAndroidSecuritySettings()) {
            postMessage("Open Android Settings → Security & privacy → Screen lock.")
        }
    }

    fun requestWorkProfileProvisioning() {
        _uiState.update {
            it.copy(
                pendingWorkSetupApp = null,
                showWorkVaultTroubleshooter = false
            )
        }
        if (!workProfileManager.isManagedProfileSupported()) {
            postMessage("Android Managed Work Profiles are not supported by this ROM.")
            return
        }
        val intent = workProfileManager.provisionWorkProfile()
        _events.tryEmit(MultiCreatorEvent.LaunchWorkProfileProvisioning(intent))
    }

    fun syncWorkVaultBridge() {
        val launched = workProfileManager.launchSelfInWorkProfile()
        if (launched) {
            postMessage("Opening Work Vault copy of MultiCreator to sync cross-profile cloning bridge…")
        } else {
            postMessage("Could not find MultiCreator inside Work Profile. Try activating Work Vault first.")
        }
    }

    fun onProvisioningResult(success: Boolean) {
        if (success) {
            _uiState.update { it.copy(showWorkVaultTroubleshooter = false) }
            postMessage("Work Vault (/data/user/10/) activated with a fresh SSAID! You can now clone native apps.")
        } else {
            val alreadyActive = workProfileManager.checkIfWorkProfileActive()
            if (!alreadyActive) {
                _uiState.update { it.copy(showWorkVaultTroubleshooter = true) }
            } else {
                postMessage("Work Vault detected! Refreshing installed apps…")
            }
        }
        refreshEcosystem()
    }

    /**
     * REAL Native App Cloner & Launcher (/data/user/10/):
     * - If the app is already genuinely installed inside Android's Work Profile -> launches THAT
     *   isolated User 10 instance via LauncherApps.startMainActivity(component, workUserHandle).
     * - If not yet installed in Work Profile -> stages the genuine unmodified base + split APKs
     *   and dispatches them across the profile boundary to WorkCloneInstallerActivity inside User 10.
     * - NEVER falls back to opening the personal profile app!
     */
    fun cloneOrLaunchNativeApp(app: InstalledAppItem) {
        if (app.isClonedInWorkProfile) {
            val launched = workProfileManager.launchAppInWorkProfile(app.packageName)
            if (!launched) {
                postMessage("Unable to launch ${app.appName} Work Clone. Make sure Work Profile is turned ON.")
            }
            return
        }

        if (_uiState.value.cloningPackageName != null) {
            postMessage("Another app clone is currently being prepared…")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(cloningPackageName = app.packageName) }
            val result = workProfileManager.prepareNativeWorkClone(app.packageName, app.appName)
            _uiState.update { it.copy(cloningPackageName = null) }

            when (result) {
                is CloneActionResult.ReadyToDispatchInstaller -> {
                    _events.emit(MultiCreatorEvent.LaunchCrossProfileIntent(result.installerIntent))
                }

                is CloneActionResult.ClonedDirectly -> {
                    postMessage("Cloned ${result.appName} into Work Vault (/data/user/10/) with a fresh SSAID!")
                    refreshEcosystem()
                }

                is CloneActionResult.AlreadyCloned -> {
                    workProfileManager.launchAppInWorkProfile(app.packageName)
                    refreshEcosystem()
                }

                is CloneActionResult.NeedsWorkProfileSetup -> {
                    _uiState.update { it.copy(pendingWorkSetupApp = app) }
                }

                is CloneActionResult.NeedsBridgeSync -> {
                    val synced = workProfileManager.launchSelfInWorkProfile()
                    if (synced) {
                        postMessage("Syncing MultiCreator Work Vault bridge once — return here and tap Clone again.")
                    } else {
                        postMessage("Open the badged MultiCreator app in your Work tab once to activate the clone bridge.")
                    }
                }

                is CloneActionResult.ForeignWorkProfile -> {
                    val hint = if (app.hasWebPortal) {
                        "Use 'Web Clone' for instant isolated accounts, or remove your existing Work Profile in Android Settings to let MultiCreator manage it."
                    } else {
                        "Another app already owns this phone's Work Profile slot. Remove it in Android Settings → Passwords & accounts → Work to let MultiCreator clone native apps."
                    }
                    postMessage(hint)
                }

                is CloneActionResult.Error -> {
                    postMessage(result.message)
                }
            }
        }
    }

    /**
     * Uninstalls a genuinely cloned native app from the Work Profile (/data/user/10/).
     */
    fun uninstallWorkClone(app: InstalledAppItem) {
        val intent = workProfileManager.buildUninstallWorkCloneIntent(app.packageName, app.appName)
        if (intent != null) {
            _events.tryEmit(MultiCreatorEvent.LaunchCrossProfileIntent(intent))
        } else {
            postMessage("Open Android Settings → Apps → Work to uninstall ${app.appName} from Work Profile.")
        }
    }

    /**
     * Instant Multi-Account Isolated Web Clone (Chromium ProfileStore):
     * Launches an existing isolated Chromium profile for this app, or creates Slot #1 and opens it.
     */
    fun openOrCreateWebClone(app: InstalledAppItem) {
        val existing = _uiState.value.clonesByPackage[app.packageName]
        if (existing != null) {
            _events.tryEmit(MultiCreatorEvent.LaunchContainer(existing))
            return
        }
        viewModelScope.launch {
            val created = containerEngine.createLocalClone(
                packageName = app.packageName,
                appName = app.appName
            )
            _events.emit(MultiCreatorEvent.LaunchContainer(created))
        }
    }

    /**
     * Spins up an additional isolated Web Clone slot for an app (e.g. Instagram #2, #3, #4).
     */
    fun createAdditionalSlot(app: InstalledAppItem) {
        viewModelScope.launch {
            val created = containerEngine.createLocalClone(
                packageName = app.packageName,
                appName = app.appName
            )
            postMessage(
                "Created ${created.displayLabel} • Fresh Chromium Profile & SSAID ${created.virtualSsaid.take(8)}."
            )
            _events.emit(MultiCreatorEvent.LaunchContainer(created))
        }
    }

    fun launchSpecificClone(container: AppContainer) {
        _events.tryEmit(MultiCreatorEvent.LaunchContainer(container))
    }

    fun deleteSpecificClone(container: AppContainer) {
        viewModelScope.launch {
            containerEngine.deleteClone(container.id)
            postMessage("Purged ${container.displayLabel} isolated Chromium profile & cookies.")
        }
    }

    fun deleteLocalClone(app: InstalledAppItem) {
        if (app.isClonedInWorkProfile) {
            uninstallWorkClone(app)
            return
        }
        val existing = _uiState.value.clonesByPackage[app.packageName] ?: return
        deleteSpecificClone(existing)
    }

    fun openGoogleAdsPrivacySettings() {
        val opened = workProfileManager.openGoogleAdsSettings()
        if (!opened) {
            postMessage("Open Android Settings → Privacy → Ads to reset your Advertising ID.")
        }
    }

    /**
     * 1-Click Add to Home Screen:
     * - If natively cloned in Work Profile -> pins a direct User 10 Work Clone shortcut!
     * - Else if a Web Clone exists (or app supports Web Clone) -> pins the isolated Chromium Web Clone shortcut!
     * - NEVER pins a shortcut that launches the original personal app!
     */
    fun pinAppShortcut(app: InstalledAppItem) {
        viewModelScope.launch {
            if (app.isClonedInWorkProfile) {
                when (val result = shortcutCreator.pinNativeWorkCloneShortcut(app)) {
                    is ShortcutPinResult.Pinned ->
                        postMessage("Pinned \"${result.label}\" Work Clone shortcut to Home Screen.")
                    is ShortcutPinResult.Unsupported ->
                        postMessage(result.reason)
                    is ShortcutPinResult.Error ->
                        postMessage(result.message)
                }
                return@launch
            }

            val existingWeb = _uiState.value.clonesByPackage[app.packageName]
            val container = existingWeb ?: if (app.hasWebPortal) {
                containerEngine.createLocalClone(app.packageName, app.appName)
            } else {
                null
            }

            if (container == null) {
                postMessage("Clone ${app.appName} into Work Vault first before pinning its Home Screen shortcut.")
                return@launch
            }

            val bitmap = app.icon?.let { drawable ->
                runCatching { drawable.toBitmap(width = 144, height = 144) }.getOrNull()
            }

            when (val result = shortcutCreator.pinClonedShortcutDetailed(container, bitmap, app.icon)) {
                is ShortcutPinResult.Pinned ->
                    postMessage("Pinned \"${result.label}\" isolated Web Clone shortcut to Home Screen.")
                is ShortcutPinResult.Unsupported ->
                    postMessage(result.reason)
                is ShortcutPinResult.Error ->
                    postMessage(result.message)
            }
        }
    }

    fun handleShortcutLaunchRequest(packageName: String, appName: String) {
        viewModelScope.launch {
            val launchedInWork = workProfileManager.launchAppInWorkProfile(packageName)
            if (launchedInWork) return@launch

            val existing = containerEngine.getClonesForPackage(packageName).firstOrNull()
            if (existing != null) {
                _events.emit(MultiCreatorEvent.LaunchContainer(existing))
                return@launch
            }

            postMessage("$appName is not cloned yet. Tap 'Clone Native' to install it in Work Vault.")
        }
    }

    fun launchWebContainer(container: WebContainerProfile) {
        viewModelScope.launch {
            containerStore.markContainerOpened(container.id)
            _events.emit(MultiCreatorEvent.OpenWebSandbox(container))
        }
    }

    fun createWebContainer(
        name: String,
        url: String,
        platformTag: String,
        userAgentMode: UserAgentMode,
        blockWebRtc: Boolean,
        accentHex: Long
    ) {
        if (name.isBlank()) {
            postMessage("Enter a container name before creating.")
            return
        }
        viewModelScope.launch {
            val created = containerStore.addContainer(
                name = name,
                url = url,
                platformTag = platformTag,
                userAgentMode = userAgentMode,
                blockWebRtc = blockWebRtc,
                accentHex = accentHex
            )
            postMessage("Created isolated container \"${created.name}\" (${created.fingerprintSeed}).")
        }
    }

    fun deleteWebContainer(container: WebContainerProfile) {
        viewModelScope.launch {
            webSandboxManager.clearContainerSession(container.id)
            containerStore.deleteContainer(container.id)
            postMessage("Wiped cookies and removed \"${container.name}\".")
        }
    }

    fun rotateContainerFingerprint(container: WebContainerProfile) {
        viewModelScope.launch {
            val newSeed = containerStore.rotateFingerprint(container.id)
            postMessage("Rotated hardware fingerprint seed for ${container.name} → $newSeed.")
        }
    }

    fun pinWebShortcut(container: WebContainerProfile) {
        when (val result = shortcutCreator.pinWebSandboxShortcut(container)) {
            is ShortcutPinResult.Pinned ->
                postMessage("Web shortcut \"${result.label}\" sent to Home Screen.")
            is ShortcutPinResult.Unsupported ->
                postMessage(result.reason)
            is ShortcutPinResult.Error ->
                postMessage(result.message)
        }
    }

    fun postMessage(message: String) {
        _uiState.update { it.copy(notificationMessage = message) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(notificationMessage = null) }
    }

    private fun sortAppsWithClones(
        apps: List<InstalledAppItem>,
        clonesByPackage: Map<String, AppContainer>
    ): List<InstalledAppItem> {
        return apps.sortedWith(
            compareByDescending<InstalledAppItem> { it.isClonedInWorkProfile }
                .thenByDescending { it.packageName in clonesByPackage }
                .thenBy { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun applyAppFilter(
        apps: List<InstalledAppItem>,
        clonesByPackage: Map<String, AppContainer>,
        query: String,
        mode: AppFilterMode
    ): List<InstalledAppItem> {
        val trimmed = query.trim().lowercase()
        return apps.filter { app ->
            val isCloned = app.isClonedInWorkProfile || app.packageName in clonesByPackage
            val matchesMode = when (mode) {
                AppFilterMode.ALL -> true
                AppFilterMode.CLONED -> isCloned
                AppFilterMode.USER -> !app.isSystemApp
                AppFilterMode.SYSTEM -> app.isSystemApp
            }
            val matchesQuery = trimmed.isEmpty() ||
                app.appName.lowercase().contains(trimmed) ||
                app.packageName.lowercase().contains(trimmed)
            matchesMode && matchesQuery
        }
    }

    private data class EcosystemRefreshSnapshot(
        val workActive: Boolean,
        val profileOwner: Boolean,
        val bridgeReady: Boolean,
        val selfInWork: Boolean,
        val supportsManaged: Boolean,
        val apps: List<InstalledAppItem>,
        val diag: DeviceHealthSnapshot
    )

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val ctx = appContext.applicationContext
            return MultiCreatorViewModel(
                containerEngine = ContainerEngine(ctx),
                workProfileManager = WorkProfileManager(ctx),
                shortcutCreator = ShortcutCreator(ctx),
                containerStore = ContainerStore(ctx),
                webSandboxManager = WebSandboxManager(ctx)
            ) as T
        }
    }
}
