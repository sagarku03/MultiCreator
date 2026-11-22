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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DpcAdminReceiver
import com.example.engine.ShortcutCreator
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.WebSandboxActivity
import com.example.ui.theme.MultiCreatorTheme
import com.example.ui.viewmodel.MultiCreatorEvent
import com.example.ui.viewmodel.MultiCreatorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MultiCreatorViewModel by viewModels {
        MultiCreatorViewModel.Factory(applicationContext)
    }

    private val workProfileProvisioningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onProvisioningResult(result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        setContent {
            MultiCreatorTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                    onCloneOrLaunchApp = viewModel::cloneOrLaunchApp,
                    onPinAppShortcut = viewModel::pinAppShortcut,
                    onLaunchWebContainer = viewModel::launchWebContainer,
                    onCreateWebContainer = viewModel::createWebContainer,
                    onRotateFingerprint = viewModel::rotateContainerFingerprint,
                    onPinWebShortcut = viewModel::pinWebShortcut,
                    onDeleteWebContainer = viewModel::deleteWebContainer,
                    onToggleSsaid = viewModel::toggleSsaidVisibility,
                    onPostMessage = viewModel::postMessage,
                    onConsumeMessage = viewModel::consumeMessage
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
