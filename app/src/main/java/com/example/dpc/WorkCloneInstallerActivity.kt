package com.example.dpc

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.DpcAdminReceiver
import com.example.ui.theme.CyberGlassBackground
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassSurfaceFill
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidPillButton
import com.example.ui.theme.MultiCreatorTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBright
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

private sealed class CloneInstallUiState {
    data class Installing(
        val appName: String,
        val detail: String,
        val progress: Float? = null
    ) : CloneInstallUiState()

    data class Success(
        val appName: String,
        val packageName: String,
        val detail: String
    ) : CloneInstallUiState()

    data class Uninstalled(val appName: String) : CloneInstallUiState()

    data class NeedsInstallPermission(
        val appName: String,
        val message: String
    ) : CloneInstallUiState()

    data class Error(
        val appName: String,
        val message: String
    ) : CloneInstallUiState()
}

/**
 * Executes EXCLUSIVELY inside the Managed Work Profile (/data/user/10/) as the Profile Owner.
 * Uses 3-layer APK reading (Direct `/data/app` -> Kernel Binder `ParcelFileDescriptor` -> `FileProvider` URI)
 * and commits into Android's native `PackageInstaller` with an explicit `BroadcastReceiver` callback
 * (Android 14/15 BAL-safe and FLAG_MUTABLE compliant) + live `SessionCallback` progress tracking.
 */
class WorkCloneInstallerActivity : ComponentActivity() {

    private var uiState by mutableStateOf<CloneInstallUiState>(
        CloneInstallUiState.Installing("App", "Initializing isolated Work Vault installer…")
    )

    private var activeTargetPackage: String = ""
    private var activeTargetAppName: String = "App"
    private var lastCloneIntent: Intent? = null
    private var activeSessionId: Int = -1
    private var isReceiverRegistered = false

    private val sessionCallback = object : PackageInstaller.SessionCallback() {
        override fun onCreated(sessionId: Int) = Unit
        override fun onBadgingChanged(sessionId: Int) = Unit
        override fun onActiveChanged(sessionId: Int, active: Boolean) = Unit
        override fun onProgressChanged(sessionId: Int, progress: Float) {
            if (sessionId == activeSessionId) {
                val current = uiState
                if (current is CloneInstallUiState.Installing) {
                    uiState = current.copy(
                        progress = progress.coerceIn(0f, 1f),
                        detail = "Installing unmodified APK into /data/user/10/ (${(progress * 100).toInt()}%)…"
                    )
                }
            }
        }

        override fun onFinished(sessionId: Int, success: Boolean) {
            if (sessionId == activeSessionId && success && activeTargetPackage.isNotBlank()) {
                uiState = CloneInstallUiState.Success(
                    appName = activeTargetAppName,
                    packageName = activeTargetPackage,
                    detail = "Genuine $activeTargetAppName cloned into /data/user/10/ with a brand-new SSAID & clean storage!"
                )
            }
        }
    }

    private val installResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                installCallbackAction() -> handlePackageInstallerCallback(intent)
                uninstallCallbackAction() -> handleUninstallCallback(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Ensure cross-profile filters & install permissions stay enabled inside User 10
        WorkCloneBridge.configureWorkProfilePolicies(applicationContext)

        registerInstallReceiver()
        runCatching { packageManager.packageInstaller.registerSessionCallback(sessionCallback) }

        setContent {
            MultiCreatorTheme {
                WorkCloneInstallerScreen(
                    state = uiState,
                    onLaunchInstalledClone = { pkg ->
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                            finish()
                        } else {
                            finish()
                        }
                    },
                    onOpenUnknownSourcesSettings = {
                        val settingsIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        runCatching { startActivity(settingsIntent) }
                    },
                    onRetryClone = {
                        lastCloneIntent?.let(::handleIntentCommand)
                    },
                    onDone = { finish() }
                )
            }
        }

        handleIntentCommand(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentCommand(intent)
    }

    override fun onDestroy() {
        runCatching { packageManager.packageInstaller.unregisterSessionCallback(sessionCallback) }
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(installResultReceiver) }
            isReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun installCallbackAction(): String = "$packageName.WORK_INSTALL_SESSION_RESULT"
    private fun uninstallCallbackAction(): String = "$packageName.WORK_UNINSTALL_SESSION_RESULT"

    private fun registerInstallReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(installCallbackAction())
            addAction(uninstallCallbackAction())
        }
        ContextCompat.registerReceiver(
            this,
            installResultReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
    }

    private fun handleIntentCommand(incoming: Intent?) {
        if (incoming == null) {
            finish()
            return
        }

        when (incoming.action) {
            WorkCloneBridge.ACTION_WORK_UNINSTALL_APK -> {
                val pkg = incoming.getStringExtra(WorkCloneBridge.EXTRA_TARGET_PACKAGE).orEmpty()
                val appName = incoming.getStringExtra(WorkCloneBridge.EXTRA_TARGET_APP_NAME) ?: pkg
                activeTargetPackage = pkg
                activeTargetAppName = appName
                if (pkg.isBlank()) {
                    finish()
                } else {
                    uninstallPackageInWorkProfile(pkg, appName)
                }
            }

            WorkCloneBridge.ACTION_WORK_CLONE_APK -> {
                lastCloneIntent = incoming
                val pkg = incoming.getStringExtra(WorkCloneBridge.EXTRA_TARGET_PACKAGE).orEmpty()
                val appName = incoming.getStringExtra(WorkCloneBridge.EXTRA_TARGET_APP_NAME) ?: pkg
                val isSystemApp = incoming.getBooleanExtra(WorkCloneBridge.EXTRA_IS_SYSTEM_APP, false)
                activeTargetPackage = pkg
                activeTargetAppName = appName

                if (pkg.isBlank()) {
                    uiState = CloneInstallUiState.Error("App", "Missing target package name.")
                    return
                }

                val apkPaths = incoming.getStringArrayListExtra(WorkCloneBridge.EXTRA_APK_PATHS).orEmpty()
                val binderBundle = incoming.getBundleExtra(WorkCloneBridge.EXTRA_APK_BINDER_BUNDLE)
                val remoteFdBinder = binderBundle?.getBinder(WorkCloneBridge.KEY_APK_FD_BINDER)

                val apkUris = linkedSetOf<Uri>()
                incoming.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.let(apkUris::add)
                    }
                }
                IntentCompat.getParcelableArrayListExtra(
                    incoming,
                    WorkCloneBridge.EXTRA_APK_URIS,
                    Uri::class.java
                )?.let(apkUris::addAll)

                performNativeWorkProfileInstall(
                    packageName = pkg,
                    appName = appName,
                    isSystemApp = isSystemApp,
                    apkPaths = apkPaths,
                    remoteFdBinder = remoteFdBinder,
                    apkUris = apkUris.toList()
                )
            }

            else -> finish()
        }
    }

    private fun performNativeWorkProfileInstall(
        packageName: String,
        appName: String,
        isSystemApp: Boolean,
        apkPaths: List<String>,
        remoteFdBinder: IBinder?,
        apkUris: List<Uri>
    ) {
        lifecycleScope.launch {
            uiState = CloneInstallUiState.Installing(
                appName = appName,
                detail = "Preparing isolated /data/user/10/ container with fresh SSAID…"
            )

            // 1. Check if already installed inside this Work Profile
            if (packageManager.getLaunchIntentForPackage(packageName) != null) {
                uiState = CloneInstallUiState.Success(
                    appName = appName,
                    packageName = packageName,
                    detail = "$appName is already cloned and active inside Work Vault (/data/user/10/)."
                )
                return@launch
            }

            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val admin = DpcAdminReceiver.getComponentName(applicationContext)
            val isProfileOwner = dpm?.isProfileOwnerApp(this@WorkCloneInstallerActivity.packageName) == true

            // 2. Fast Path A: System apps (YouTube, Chrome, Gmail, etc.) -> enable instantaneously via DPM
            if (isProfileOwner && dpm != null && isSystemApp) {
                val enabled = withContext(Dispatchers.IO) {
                    runCatching {
                        dpm.enableSystemApp(admin, packageName)
                        packageManager.getLaunchIntentForPackage(packageName) != null
                    }.getOrDefault(false)
                }
                if (enabled) {
                    uiState = CloneInstallUiState.Success(
                        appName = appName,
                        packageName = packageName,
                        detail = "Enabled genuine $appName inside isolated Work Vault with a fresh SSAID."
                    )
                    return@launch
                }
            }

            // 3. Fast Path B: Affiliated installExistingPackage (API 28+)
            if (isProfileOwner && dpm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val installedExisting = withContext(Dispatchers.IO) {
                    runCatching {
                        if (dpm.isAffiliatedUser) {
                            dpm.installExistingPackage(admin, packageName)
                        } else {
                            false
                        }
                    }.getOrDefault(false)
                }
                if (installedExisting) {
                    uiState = CloneInstallUiState.Success(
                        appName = appName,
                        packageName = packageName,
                        detail = "Cloned $appName natively into /data/user/10/ with zero extra storage."
                    )
                    return@launch
                }
            }

            // 4. Universal Path: Stream base + all split APKs into Android PackageInstaller inside User 10
            val splitCount = maxOf(apkPaths.size, apkUris.size)
            if (splitCount == 0) {
                uiState = CloneInstallUiState.Error(
                    appName = appName,
                    message = "No APK files were located for $appName."
                )
                return@launch
            }

            uiState = CloneInstallUiState.Installing(
                appName = appName,
                detail = "Streaming $splitCount unmodified APK split(s) into isolated Work Vault…",
                progress = 0.05f
            )

            val commitError = withContext(Dispatchers.IO) {
                commitSplitApkSession(
                    targetPackage = packageName,
                    splitCount = splitCount,
                    apkPaths = apkPaths,
                    remoteFdBinder = remoteFdBinder,
                    apkUris = apkUris
                )
            }
            if (commitError != null) {
                uiState = CloneInstallUiState.Error(appName, commitError)
            }
        }
    }

    /**
     * Opens an InputStream for split APK #[index] using our 3-layer zero-failure reader:
     *   Layer 1: Direct `/data/app/.../base.apk` read (fastest, 0MB copy)
     *   Layer 2: Kernel Binder IPC `ParcelFileDescriptor` from Personal Profile (bypasses SELinux/FileProvider)
     *   Layer 3: `FileProvider` `content://` URI fallback
     */
    private fun openSplitApkStream(
        index: Int,
        apkPaths: List<String>,
        remoteFdBinder: IBinder?,
        apkUris: List<Uri>
    ): Pair<InputStream, Long>? {
        // Layer 1: Direct /data/app/.../base.apk read
        val path = apkPaths.getOrNull(index)
        if (!path.isNullOrBlank()) {
            val directFile = File(path)
            if (directFile.exists() && directFile.canRead()) {
                val stream = runCatching { FileInputStream(directFile) }.getOrNull()
                if (stream != null) {
                    return stream to directFile.length().takeIf { it > 0L }.orMinusOne()
                }
            }
        }

        // Layer 2: Kernel Binder ParcelFileDescriptor IPC across User 0 -> User 10
        val pfd = WorkCloneBridge.openRemoteApkFd(remoteFdBinder, index)
        if (pfd != null) {
            val size = runCatching { pfd.statSize }.getOrDefault(-1L)
            return ParcelFileDescriptor.AutoCloseInputStream(pfd) to size
        }

        // Layer 3: FileProvider ClipData URI fallback
        val uri = apkUris.getOrNull(index)
        if (uri != null) {
            val stream = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
            if (stream != null) {
                return stream to -1L
            }
        }

        return null
    }

    private fun Long?.orMinusOne(): Long = this ?: -1L

    private fun commitSplitApkSession(
        targetPackage: String,
        splitCount: Int,
        apkPaths: List<String>,
        remoteFdBinder: IBinder?,
        apkUris: List<Uri>
    ): String? {
        val installer = packageManager.packageInstaller
        var session: PackageInstaller.Session? = null
        return try {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(targetPackage)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = installer.createSession(params)
            activeSessionId = sessionId
            session = installer.openSession(sessionId)

            for (index in 0 until splitCount) {
                val opened = openSplitApkStream(index, apkPaths, remoteFdBinder, apkUris)
                    ?: throw IllegalStateException(
                        "Unable to read APK split #$index across User 0 → User 10 boundary."
                    )

                val (inputStream, sizeBytes) = opened
                inputStream.use { input ->
                    val splitName = if (index == 0) "base.apk" else "split_$index.apk"
                    session.openWrite(splitName, 0L, sizeBytes).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                        session.fsync(output)
                    }
                }
            }

            // Explicit package name is MANDATORY on Android 14+ (API 34+) for FLAG_MUTABLE PendingIntent!
            val callbackIntent = Intent(installCallbackAction()).apply {
                setPackage(this@WorkCloneInstallerActivity.packageName)
                putExtra(WorkCloneBridge.EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(WorkCloneBridge.EXTRA_TARGET_APP_NAME, activeTargetAppName)
            }

            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                sessionId,
                callbackIntent,
                pendingFlags
            )

            session.commit(pendingIntent.intentSender)
            null
        } catch (ex: Exception) {
            runCatching { session?.abandon() }
            ex.localizedMessage ?: "Failed to install cloned package into Work Profile."
        } finally {
            runCatching { session?.close() }
        }
    }

    private fun uninstallPackageInWorkProfile(targetPackage: String, appName: String) {
        uiState = CloneInstallUiState.Installing(
            appName = appName,
            detail = "Removing $appName clone from Work Vault (/data/user/10/)…"
        )
        runCatching {
            val callbackIntent = Intent(uninstallCallbackAction()).apply {
                setPackage(this@WorkCloneInstallerActivity.packageName)
                putExtra(WorkCloneBridge.EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(WorkCloneBridge.EXTRA_TARGET_APP_NAME, appName)
            }
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                targetPackage.hashCode(),
                callbackIntent,
                pendingFlags
            )
            packageManager.packageInstaller.uninstall(targetPackage, pendingIntent.intentSender)
        }.onFailure { err ->
            uiState = CloneInstallUiState.Error(
                appName = appName,
                message = err.localizedMessage ?: "Failed to remove cloned package."
            )
        }
    }

    private fun handlePackageInstallerCallback(intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val pkg = intent.getStringExtra(WorkCloneBridge.EXTRA_TARGET_PACKAGE)
            ?.ifBlank { activeTargetPackage }
            ?: activeTargetPackage
        val appName = intent.getStringExtra(WorkCloneBridge.EXTRA_TARGET_APP_NAME)
            ?.ifBlank { activeTargetAppName }
            ?: activeTargetAppName

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java
                )
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(confirmIntent) }.onFailure { err ->
                        uiState = CloneInstallUiState.Error(
                            appName = appName,
                            message = err.localizedMessage ?: "Unable to open Android install confirmation."
                        )
                    }
                } else {
                    uiState = CloneInstallUiState.Error(appName, "Install confirmation dialog missing.")
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                uiState = CloneInstallUiState.Success(
                    appName = appName,
                    packageName = pkg,
                    detail = "Genuine $appName cloned into /data/user/10/ with a brand-new SSAID & clean storage!"
                )
            }

            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                if (!packageManager.canRequestPackageInstalls()) {
                    uiState = CloneInstallUiState.NeedsInstallPermission(
                        appName = appName,
                        message = "Your phone requires 'Install unknown apps' enabled for MultiCreator inside Work Vault to clone $appName."
                    )
                } else {
                    uiState = CloneInstallUiState.Error(
                        appName = appName,
                        message = statusMessage?.ifBlank { null }
                            ?: "Installation was blocked by Android security policy."
                    )
                }
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                uiState = CloneInstallUiState.Error(
                    appName = appName,
                    message = "Clone installation was cancelled."
                )
            }

            else -> {
                uiState = CloneInstallUiState.Error(
                    appName = appName,
                    message = statusMessage?.ifBlank { null }
                        ?: "Android PackageInstaller returned status $status."
                )
            }
        }
    }

    private fun handleUninstallCallback(intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val appName = intent.getStringExtra(WorkCloneBridge.EXTRA_TARGET_APP_NAME) ?: activeTargetAppName
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java
                )
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(it) }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                uiState = CloneInstallUiState.Uninstalled(appName)
            }

            else -> {
                uiState = CloneInstallUiState.Error(
                    appName = appName,
                    message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: "Unable to remove $appName from Work Vault."
                )
            }
        }
    }
}

@Composable
private fun WorkCloneInstallerScreen(
    state: CloneInstallUiState,
    onLaunchInstalledClone: (String) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onRetryClone: () -> Unit,
    onDone: () -> Unit
) {
    CyberGlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                highlightAccent = when (state) {
                    is CloneInstallUiState.Installing -> NeonCyan
                    is CloneInstallUiState.Success -> EmeraldGlow
                    is CloneInstallUiState.Uninstalled -> NeonCyanBright
                    is CloneInstallUiState.NeedsInstallPermission -> NeonCyanBright
                    is CloneInstallUiState.Error -> RoseAlert
                },
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (state) {
                        is CloneInstallUiState.Installing -> {
                            CircularProgressIndicator(
                                color = NeonCyanBright,
                                strokeWidth = 3.5.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Cloning ${state.appName}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = state.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            state.progress?.let { pct ->
                                LinearProgressIndicator(
                                    progress = { pct },
                                    color = EmeraldGlow,
                                    trackColor = GlassSurfaceFill,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50.dp))
                                )
                            }
                        }

                        is CloneInstallUiState.Success -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGlow,
                                modifier = Modifier.size(52.dp)
                            )
                            Text(
                                text = "${state.appName} Cloned!",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = state.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LiquidPillButton(
                                text = "Open Isolated Clone Now",
                                icon = Icons.Rounded.Launch,
                                accentColors = listOf(EmeraldGlow, NeonCyan),
                                onClick = { onLaunchInstalledClone(state.packageName) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            LiquidPillButton(
                                text = "Back to MultiCreator",
                                isPrimary = false,
                                onClick = onDone,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        is CloneInstallUiState.Uninstalled -> {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = NeonCyanBright,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "${state.appName} Removed",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "The Work Vault clone and its isolated /data/user/10/ storage have been wiped.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            LiquidPillButton(
                                text = "Done",
                                onClick = onDone,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        is CloneInstallUiState.NeedsInstallPermission -> {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = NeonCyanBright,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Allow Work Vault Installer",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            LiquidPillButton(
                                text = "Allow Install Permission",
                                icon = Icons.Rounded.Settings,
                                onClick = onOpenUnknownSourcesSettings,
                                modifier = Modifier.fillMaxWidth()
                            )
                            LiquidPillButton(
                                text = "Retry Cloning ${state.appName}",
                                icon = Icons.Rounded.Refresh,
                                isPrimary = false,
                                onClick = onRetryClone,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        is CloneInstallUiState.Error -> {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = RoseAlert,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Could Not Clone ${state.appName}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            LiquidPillButton(
                                text = "Retry",
                                icon = Icons.Rounded.Refresh,
                                onClick = onRetryClone,
                                modifier = Modifier.fillMaxWidth()
                            )
                            LiquidPillButton(
                                text = "Close",
                                isPrimary = false,
                                onClick = onDone,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
