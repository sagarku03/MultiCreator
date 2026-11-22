package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ContainerStore
import com.example.data.UserAgentMode
import com.example.data.WebContainerProfile
import com.example.engine.CloneActionResult
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
    data class OpenWebSandbox(val container: WebContainerProfile) : MultiCreatorEvent()
}

class MultiCreatorViewModel(
    private val workProfileManager: WorkProfileManager,
    private val shortcutCreator: ShortcutCreator,
    private val containerStore: ContainerStore,
    private val webSandboxManager: WebSandboxManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiCreatorUiState())
    val uiState: StateFlow<MultiCreatorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MultiCreatorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MultiCreatorEvent> = _events.asSharedFlow()

    init {
        observeWebContainers()
        refreshEcosystem()
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
                val filtered = applyAppFilter(apps, state.searchQuery, state.appFilterMode)
                state.copy(
                    isLoadingApps = false,
                    installedApps = apps,
                    filteredApps = filtered,
                    isWorkProfileActive = profileFlags.first,
                    isProfileOwner = profileFlags.second,
                    supportsManagedProfiles = profileFlags.third,
                    clonedCount = apps.count { it.isClonedInWorkProfile },
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
                filteredApps = applyAppFilter(state.installedApps, query, state.appFilterMode)
            )
        }
    }

    fun onFilterModeChange(mode: AppFilterMode) {
        _uiState.update { state ->
            state.copy(
                appFilterMode = mode,
                filteredApps = applyAppFilter(state.installedApps, state.searchQuery, mode)
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

    fun cloneOrLaunchApp(app: InstalledAppItem) {
        if (app.isClonedInWorkProfile) {
            val launched = workProfileManager.launchAppInWorkProfile(app.packageName)
            if (!launched) {
                postMessage("Unable to launch ${app.appName} in Work Profile.")
            }
            return
        }

        when (val result = workProfileManager.cloneAppToWorkProfile(app.packageName, app.appName)) {
            is CloneActionResult.ClonedDirectly -> {
                postMessage("${result.appName} enabled inside isolated Work Profile.")
                refreshEcosystem()
            }
            is CloneActionResult.OpenedWorkStore -> {
                postMessage("Opened Play Store for ${result.appName} — install to Work Profile for Play Integrity safety.")
            }
            is CloneActionResult.NeedsWorkProfileSetup -> {
                postMessage("Initialize the Work Profile Vault first to clone ${result.appName}.")
                requestWorkProfileProvisioning()
            }
            is CloneActionResult.AlreadyCloned -> {
                workProfileManager.launchAppInWorkProfile(app.packageName)
            }
            is CloneActionResult.Error -> {
                postMessage(result.message)
            }
        }
    }

    fun handleShortcutLaunchRequest(packageName: String, appName: String) {
        val launchedInWork = workProfileManager.launchAppInWorkProfile(packageName)
        if (!launchedInWork) {
            val launchedPersonal = workProfileManager.launchPersonalApp(packageName)
            if (!launchedPersonal) {
                postMessage("$appName is not installed in the active profile.")
            } else {
                postMessage("Work Profile inactive — opened $appName in personal space.")
            }
        }
    }

    fun pinAppShortcut(app: InstalledAppItem) {
        when (val result = shortcutCreator.pinClonedAppShortcut(app)) {
            is ShortcutPinResult.Pinned ->
                postMessage("Shortcut \"${result.label}\" sent to Home Screen.")
            is ShortcutPinResult.Unsupported ->
                postMessage(result.reason)
            is ShortcutPinResult.Error ->
                postMessage(result.message)
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

    private fun applyAppFilter(
        apps: List<InstalledAppItem>,
        query: String,
        mode: AppFilterMode
    ): List<InstalledAppItem> {
        val trimmed = query.trim().lowercase()
        return apps.filter { app ->
            val matchesMode = when (mode) {
                AppFilterMode.ALL -> true
                AppFilterMode.CLONED -> app.isClonedInWorkProfile
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
                workProfileManager = WorkProfileManager(ctx),
                shortcutCreator = ShortcutCreator(ctx),
                containerStore = ContainerStore(ctx),
                webSandboxManager = WebSandboxManager(ctx)
            ) as T
        }
    }
}
