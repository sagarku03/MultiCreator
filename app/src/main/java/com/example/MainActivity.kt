package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DpcAdminReceiver
import com.example.engine.GitHubUpdater
import com.example.engine.InstallDispatchResult
import com.example.engine.ShortcutCreator
import com.example.engine.UpdateInfo
import com.example.ui.components.UpdateDialog
import com.example.ui.components.UpdateDialogState
import com.example.ui.screens.ContainerHostActivity
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.WebSandboxActivity
import com.example.ui.theme.MultiCreatorTheme
import com.example.ui.viewmodel.MultiCreatorEvent
import com.example.ui.viewmodel.MultiCreatorViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MultiCreatorViewModel by viewModels {
        MultiCreatorViewModel.Factory(applicationContext)
    }

    private lateinit var gitHubUpdater: GitHubUpdater
    private var pendingInstallFile: File? = null
    private var onUnknownSourcesReturned: (() -> Unit)? = null

    private val workProfileProvisioningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onProvisioningResult(result.resultCode == RESULT_OK)
    }

    private val unknownSourcesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onUnknownSourcesReturned?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        gitHubUpdater = GitHubUpdater(applicationContext)
        // If this instance is running inside the Work Profile (User 10) as Profile Owner,
        // ensure the cross-profile APK cloning bridge is always registered and enabled.
        com.example.dpc.WorkCloneBridge.configureWorkProfilePolicies(applicationContext)
        handleIncomingIntent(intent)

        setContent {
            MultiCreatorTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val coroutineScope = rememberCoroutineScope()

                var updateDialogState by remember {
                    mutableStateOf<UpdateDialogState>(UpdateDialogState.Hidden)
                }
                var isCheckingUpdates by remember { mutableStateOf(false) }
                var activeDownloadJob by remember { mutableStateOf<Job?>(null) }

                fun dispatchApkInstall(apkFile: File, currentInfo: UpdateInfo?) {
                    when (val res = gitHubUpdater.installApk(apkFile)) {
                        InstallDispatchResult.LaunchedInstaller -> {
                            pendingInstallFile = null
                        }

                        is InstallDispatchResult.NeedsUnknownSourcesPermission -> {
                            pendingInstallFile = apkFile
                            if (currentInfo != null) {
                                updateDialogState = UpdateDialogState.ReadyToInstall(
                                    info = currentInfo,
                                    apkFile = apkFile,
                                    needsPermission = true
                                )
                            }
                            onUnknownSourcesReturned = {
                                val file = pendingInstallFile
                                if (file != null && gitHubUpdater.canInstallPackages()) {
                                    if (currentInfo != null) {
                                        updateDialogState = UpdateDialogState.ReadyToInstall(
                                            info = currentInfo,
                                            apkFile = file,
                                            needsPermission = false
                                        )
                                    }
                                    gitHubUpdater.installApk(file)
                                }
                            }
                            runCatching {
                                unknownSourcesPermissionLauncher.launch(res.settingsIntent)
                            }.onFailure {
                                viewModel.postMessage("Please allow \"Install unknown apps\" in Android Settings.")
                            }
                        }

                        is InstallDispatchResult.Error -> {
                            if (currentInfo != null) {
                                updateDialogState = UpdateDialogState.Error(currentInfo, res.message)
                            } else {
                                viewModel.postMessage(res.message)
                            }
                        }
                    }
                }

                fun triggerUpdateCheck(showUpToDateToast: Boolean) {
                    if (isCheckingUpdates) return
                    coroutineScope.launch {
                        isCheckingUpdates = true
                        val latest = gitHubUpdater.checkForUpdate(BuildConfig.VERSION_NAME)
                        isCheckingUpdates = false
                        if (latest != null) {
                            updateDialogState = UpdateDialogState.Available(latest)
                        } else if (showUpToDateToast) {
                            viewModel.postMessage("MultiCreator v${BuildConfig.VERSION_NAME} is up to date.")
                        }
                    }
                }

                // Non-blocking GitHub Releases auto-update check on app launch
                LaunchedEffect(Unit) {
                    triggerUpdateCheck(showUpToDateToast = false)
                }

                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is MultiCreatorEvent.LaunchWorkProfileProvisioning -> {
                                runCatching {
                                    workProfileProvisioningLauncher.launch(event.intent)
                                }.onFailure {
                                    viewModel.postMessage(
                                        "Managed profile provisioning is not available on this device ROM."
                                    )
                                }
                            }

                            is MultiCreatorEvent.LaunchCrossProfileIntent -> {
                                runCatching {
                                    startActivity(event.intent)
                                }.onFailure { err ->
                                    viewModel.postMessage(
                                        err.localizedMessage
                                            ?: "Unable to route clone request to Work Vault. Open MultiCreator in Work tab once to sync."
                                    )
                                }
                            }

                            is MultiCreatorEvent.LaunchContainer -> {
                                val containerIntent = ContainerHostActivity.createLaunchIntent(
                                    context = this@MainActivity,
                                    container = event.container
                                )
                                startActivity(containerIntent)
                            }

                            is MultiCreatorEvent.OpenWebSandbox -> {
                                val sandboxIntent = WebSandboxActivity.createLaunchIntent(
                                    context = this@MainActivity,
                                    container = event.container
                                )
                                startActivity(sandboxIntent)
                            }
                        }
                    }
                }

                HomeScreen(
                    uiState = uiState,
                    onSelectTab = viewModel::selectTab,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onFilterModeChange = viewModel::onFilterModeChange,
                    onRefresh = viewModel::refreshEcosystem,
                    onProvisionWorkProfile = viewModel::requestWorkProfileProvisioning,
                    onSyncWorkBridge = viewModel::syncWorkVaultBridge,
                    onDismissWorkSetupDialog = viewModel::dismissWorkSetupDialog,
                    onDismissWorkVaultTroubleshooter = viewModel::dismissWorkVaultTroubleshooter,
                    onOpenAccountsSettings = viewModel::openAndroidAccountsSettings,
                    onOpenSecuritySettings = viewModel::openAndroidSecuritySettings,
                    onCloneOrLaunchNativeApp = viewModel::cloneOrLaunchNativeApp,
                    onOpenOrCreateWebClone = viewModel::openOrCreateWebClone,
                    onCreateAdditionalSlot = viewModel::createAdditionalSlot,
                    onLaunchSpecificClone = viewModel::launchSpecificClone,
                    onDeleteLocalClone = viewModel::deleteLocalClone,
                    onPinAppShortcut = viewModel::pinAppShortcut,
                    onLaunchWebContainer = viewModel::launchWebContainer,
                    onCreateWebContainer = viewModel::createWebContainer,
                    onRotateFingerprint = viewModel::rotateContainerFingerprint,
                    onPinWebShortcut = viewModel::pinWebShortcut,
                    onDeleteWebContainer = viewModel::deleteWebContainer,
                    onToggleSsaid = viewModel::toggleSsaidVisibility,
                    onOpenGoogleAdsSettings = viewModel::openGoogleAdsPrivacySettings,
                    isCheckingUpdates = isCheckingUpdates,
                    onCheckForUpdates = { triggerUpdateCheck(showUpToDateToast = true) },
                    onPostMessage = viewModel::postMessage,
                    onConsumeMessage = viewModel::consumeMessage
                )

                UpdateDialog(
                    state = updateDialogState,
                    onStartDownload = { info ->
                        activeDownloadJob?.cancel()
                        updateDialogState = UpdateDialogState.Downloading(
                            info = info,
                            progress = 0f,
                            bytesDownloaded = 0L,
                            totalBytes = info.assetSizeBytes
                        )
                        activeDownloadJob = coroutineScope.launch {
                            gitHubUpdater.downloadApk(
                                downloadUrl = info.downloadUrl,
                                fileName = info.assetName,
                                onProgress = { progress, readBytes, totalBytes ->
                                    updateDialogState = UpdateDialogState.Downloading(
                                        info = info,
                                        progress = progress,
                                        bytesDownloaded = readBytes,
                                        totalBytes = totalBytes
                                    )
                                },
                                onComplete = { apkFile ->
                                    updateDialogState = UpdateDialogState.ReadyToInstall(
                                        info = info,
                                        apkFile = apkFile,
                                        needsPermission = !gitHubUpdater.canInstallPackages()
                                    )
                                },
                                onError = { errorMsg ->
                                    updateDialogState = UpdateDialogState.Error(info, errorMsg)
                                }
                            )
                        }
                    },
                    onCancelDownload = {
                        activeDownloadJob?.cancel()
                        activeDownloadJob = null
                        updateDialogState = UpdateDialogState.Hidden
                        viewModel.postMessage("Update download cancelled.")
                    },
                    onInstallApk = { apkFile ->
                        val info = (updateDialogState as? UpdateDialogState.ReadyToInstall)?.info
                        dispatchApkInstall(apkFile, info)
                    },
                    onDismiss = {
                        activeDownloadJob?.cancel()
                        activeDownloadJob = null
                        updateDialogState = UpdateDialogState.Hidden
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEcosystem()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(DpcAdminReceiver.EXTRA_PROVISIONING_JUST_COMPLETED, false)) {
            viewModel.onProvisioningResult(true)
            return
        }
        if (intent.action == ShortcutCreator.ACTION_LAUNCH_CLONED_APP) {
            val pkg = intent.getStringExtra(ShortcutCreator.EXTRA_TARGET_PACKAGE)
            val label = intent.getStringExtra(ShortcutCreator.EXTRA_TARGET_APP_NAME) ?: "App"
            if (!pkg.isNullOrBlank()) {
                viewModel.handleShortcutLaunchRequest(pkg, label)
            }
        }
    }
}
