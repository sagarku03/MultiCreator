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
    val searchQuery: String = "",
    val appFilterMode: AppFilterMode = AppFilterMode.ALL,
    val isWorkProfileActive: Boolean = false,
    val isProfileOwner: Boolean = false,
    val supportsManagedProfiles: Boolean = true,
    val clonedCount: Int = 0,
    val webContainers: List<WebContainerProfile> = emptyList(),
    val diagnostics: DeviceHealthSnapshot? = null,
    val isSsaidRevealed: Boolean = false,
    val notificationMessage: String? = null
)

sealed class MultiCreatorEvent {
    data class LaunchWorkProfileProvisioning(val intent: Intent) : MultiCreatorEvent()
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

    private val _events = MutableSharedFlow<MultiCreatorEvent>(extraBufferCapacity = 6)
    val events: SharedFlow<MultiCreatorEvent> = _events.asSharedFlow()

    init {
        observeLocalClones()
        observeWebContainers()
        refreshEcosystem()
    }

    private fun observeLocalClones() {
        viewModelScope.launch {
            containerEngine.containersFlow.collect { clones ->
                val byPackage = clones.associateBy { it.packageName }
                _uiState.update { state ->
                    val mergedApps = sortAppsWithClones(state.installedApps, byPackage)
                    val filtered = applyAppFilter(mergedApps, byPackage, state.searchQuery, state.appFilterMode)
                    state.copy(
                        localClones = clones,
                        clonesByPackage = byPackage,
                        installedApps = mergedApps,
                        filteredApps = filtered,
                        clonedCount = clones.size + mergedApps.count {
                            it.isClonedInWorkProfile && it.packageName !in byPackage
                        }
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
            val snapshot = withContext(Dispatchers.IO) {
                val workActive = workProfileManager.checkIfWorkProfileActive()
                val profileOwner = workProfileManager.isProfileOwner()
                val supportsManaged = workProfileManager.isManagedProfileSupported()
                val apps = workProfileManager.getInstalledApps()
                val diag = workProfileManager.getDeviceDiagnostics(_uiState.value.webContainers.size)
                Triple(Triple(workActive, profileOwner, supportsManaged), apps, diag)
            }
            val (profileFlags, apps, diag) = snapshot
            _uiState.update { state ->
                val byPackage = state.clonesByPackage
                val sorted = sortAppsWithClones(apps, byPackage)
                val filtered = applyAppFilter(sorted, byPackage, state.searchQuery, state.appFilterMode)
                state.copy(
                    isLoadingApps = false,
                    installedApps = sorted,
                    filteredApps = filtered,
                    isWorkProfileActive = profileFlags.first,
                    isProfileOwner = profileFlags.second,
                    supportsManagedProfiles = profileFlags.third,
                    clonedCount = state.localClones.size + sorted.count {
                        it.isClonedInWorkProfile && it.packageName !in byPackage
                    },
                    diagnostics = diag
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

    fun requestWorkProfileProvisioning() {
        if (!workProfileManager.isManagedProfileSupported()) {
            postMessage("Managed Work Profiles are not supported by this Android build.")
            return
        }
        val intent = workProfileManager.provisionWorkProfile()
        _events.tryEmit(MultiCreatorEvent.LaunchWorkProfileProvisioning(intent))
    }

    fun onProvisioningResult(success: Boolean) {
        if (success) {
            postMessage("Work Profile Vault initialized with fresh SSAID partition.")
        } else {
            postMessage("Work Profile setup was cancelled or deferred.")
        }
        refreshEcosystem()
    }

    /**
     * Instant 1-Click Local App Cloner & Launcher:
     * - If already cloned locally -> Launches ContainerHostActivity immediately.
     * - If not yet cloned -> Instantly creates an isolated local container with its own
     *   private directory, SharedPreferences, cookie vault, and virtual SSAID.
     */
    fun cloneOrLaunchApp(app: InstalledAppItem) {
        val existingLocalClone = _uiState.value.clonesByPackage[app.packageName]
        if (existingLocalClone != null) {
            _events.tryEmit(MultiCreatorEvent.LaunchContainer(existingLocalClone))
            return
        }

        viewModelScope.launch {
            val created = containerEngine.createLocalClone(
                packageName = app.packageName,
                appName = app.appName
            )
            postMessage(
                "Cloned ${app.appName} • Isolated SSAID ${created.virtualSsaid.take(8)} ready."
            )
        }
    }

    /**
     * Wipes a cloned app's isolated filesystem directory and virtual identity.
     */
    fun deleteLocalClone(app: InstalledAppItem) {
        val existing = _uiState.value.clonesByPackage[app.packageName] ?: return
        viewModelScope.launch {
            containerEngine.deleteClone(existing.id)
            postMessage("Removed isolated clone and wiped container data for ${app.appName}.")
        }
    }

    /**
     * 1-Click Add to Home Screen:
     * Ensures an isolated local container exists for the app (auto-cloning if needed),
     * then pins a badged shortcut targeting ContainerHostActivity with EXTRA_CONTAINER_ID.
     */
    fun pinAppShortcut(app: InstalledAppItem) {
        viewModelScope.launch {
            val container = _uiState.value.clonesByPackage[app.packageName]
                ?: containerEngine.createLocalClone(app.packageName, app.appName)

            val bitmap = app.icon?.let { drawable ->
                runCatching { drawable.toBitmap(width = 144, height = 144) }.getOrNull()
            }

            when (val result = shortcutCreator.pinClonedShortcutDetailed(container, bitmap, app.icon)) {
                is ShortcutPinResult.Pinned ->
                    postMessage("Pinned \"${result.label}\" shortcut directly to Home Screen.")
                is ShortcutPinResult.Unsupported ->
                    postMessage(result.reason)
                is ShortcutPinResult.Error ->
                    postMessage(result.message)
            }
        }
    }

    fun handleShortcutLaunchRequest(packageName: String, appName: String) {
        viewModelScope.launch {
            val existing = containerEngine.getClonesForPackage(packageName).firstOrNull()
            if (existing != null) {
                _events.emit(MultiCreatorEvent.LaunchContainer(existing))
                return@launch
            }
            val launchedInWork = workProfileManager.launchAppInWorkProfile(packageName)
            if (!launchedInWork) {
                val created = containerEngine.createLocalClone(packageName, appName)
                _events.emit(MultiCreatorEvent.LaunchContainer(created))
            }
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
            compareByDescending<InstalledAppItem> {
                it.packageName in clonesByPackage || it.isClonedInWorkProfile
            }
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
            val isCloned = app.packageName in clonesByPackage || app.isClonedInWorkProfile
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
