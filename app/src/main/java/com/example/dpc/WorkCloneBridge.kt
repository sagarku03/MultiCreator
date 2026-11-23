package com.example.dpc

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.DpcAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real zero-root cross-profile cloning bridge (Shelter / Android Enterprise architecture).
 *
 * How native cloning works without root or APK repackaging:
 * 1. MultiCreator provisions an official Android Managed Work Profile (/data/user/10/)
 *    with a fresh SSAID and completely isolated storage partition.
 * 2. Inside User 10, MultiCreator runs as the Profile Owner (DPC) and registers a
 *    FLAG_PARENT_CAN_ACCESS_MANAGED cross-profile IntentFilter while enabling
 *    WorkCloneInstallerActivity ONLY in the Work Profile.
 * 3. When the user taps "Clone Native App" from the Personal Profile (User 0), this bridge
 *    stages the app's original, unmodified base.apk + all split APKs into FileProvider cache
 *    and sends them via ClipData + FLAG_GRANT_READ_URI_PERMISSION straight into User 10.
 * 4. WorkCloneInstallerActivity inside User 10 installs the genuine package into /data/user/10/
 *    using DevicePolicyManager / PackageInstaller — preserving original signatures and Play Integrity.
 */
object WorkCloneBridge {

    private const val TAG = "WorkCloneBridge"

    const val ACTION_WORK_CLONE_APK = "com.example.action.WORK_CLONE_APK"
    const val ACTION_WORK_UNINSTALL_APK = "com.example.action.WORK_UNINSTALL_APK"
    const val ACTION_LAUNCH_WORK_SHORTCUT = "com.example.action.LAUNCH_WORK_SHORTCUT"

    const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    const val EXTRA_TARGET_APP_NAME = "extra_target_app_name"
    const val EXTRA_IS_SYSTEM_APP = "extra_is_system_app"
    const val EXTRA_APK_URIS = "extra_apk_uris"

    /**
     * Configures the Work Profile when running inside User 10 as Profile Owner:
     * - Names and enables the Managed Profile.
     * - Enables WorkCloneInstallerActivity inside User 10 (disabled in Personal Profile so
     *   cross-profile intents always jump straight to User 10 without a chooser dialog).
     * - Registers cross-profile intent filters allowing Personal MultiCreator to send clone/uninstall commands.
     */
    fun configureWorkProfilePolicies(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        if (!dpm.isProfileOwnerApp(context.packageName)) {
            return false
        }

        val admin = DpcAdminReceiver.getComponentName(context)
        return runCatching {
            dpm.setProfileName(admin, DpcAdminReceiver.PROFILE_DISPLAY_NAME)
            dpm.setProfileEnabled(admin)

            // Enable the Work Profile installer activity ONLY inside the Work Profile
            val installerComponent = ComponentName(context, WorkCloneInstallerActivity::class.java)
            context.packageManager.setComponentEnabledSetting(
                installerComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Clear stale cross-profile filters before re-registering idempotently
            runCatching { dpm.clearCrossProfileIntentFilters(admin) }

            val cloneFilter = IntentFilter(ACTION_WORK_CLONE_APK).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addCrossProfileIntentFilter(
                admin,
                cloneFilter,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
            )

            val uninstallFilter = IntentFilter(ACTION_WORK_UNINSTALL_APK).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addCrossProfileIntentFilter(
                admin,
                uninstallFilter,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
            )

            // Allow user to install apps from MultiCreator inside the Work Profile
            runCatching {
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_APPS)
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            }

            Log.i(TAG, "Configured Work Profile cross-profile cloning bridge successfully.")
            true
        }.onFailure { err ->
            Log.e(TAG, "Failed configuring Work Profile policies", err)
        }.getOrDefault(false)
    }

    /**
     * Returns true when called from the Personal Profile and Android resolves our
     * ACTION_WORK_CLONE_APK cross-profile intent into the Work Profile forwarder.
     */
    fun isCrossProfileCloneRouteReady(context: Context): Boolean {
        val probe = Intent(ACTION_WORK_CLONE_APK).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        return runCatching {
            val resolved = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            resolved != null
        }.getOrDefault(false)
    }

    /**
     * Stages an installed app's unmodified base APK + all split APKs into MultiCreator's
     * FileProvider cache and builds a cross-profile Intent targeting WorkCloneInstallerActivity
     * inside the Work Profile (/data/user/10/).
     */
    suspend fun buildCrossProfileCloneIntent(
        context: Context,
        packageName: String,
        appName: String
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val sourcePaths = buildList {
                appInfo.publicSourceDir?.takeIf { it.isNotBlank() }?.let(::add)
                appInfo.splitPublicSourceDirs?.filter { it.isNotBlank() }?.let(::addAll)
            }.distinct()

            val stageRoot = File(context.cacheDir, "clone_stage").apply { mkdirs() }
            // Keep cache tidy by removing previous staged packages
            stageRoot.listFiles()?.forEach { old ->
                if (old.isDirectory) old.deleteRecursively() else old.delete()
            }

            val pkgDir = File(stageRoot, packageName.replace(Regex("[^a-zA-Z0-9._-]"), "_")).apply {
                mkdirs()
            }

            val authority = "${context.packageName}.fileprovider"
            val uris = ArrayList<Uri>()

            sourcePaths.forEachIndexed { index, srcPath ->
                val srcFile = File(srcPath)
                if (srcFile.exists() && srcFile.canRead()) {
                    val outName = if (index == 0) "base.apk" else "split_${index}_${srcFile.name}"
                    val destFile = File(pkgDir, outName)
                    srcFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    }
                    val contentUri = FileProvider.getUriForFile(context, authority, destFile)
                    uris.add(contentUri)
                }
            }

            if (uris.isEmpty() && !isSystemApp) {
                throw IllegalStateException(
                    "Unable to read installed APK files for $appName."
                )
            }

            Intent(ACTION_WORK_CLONE_APK).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(EXTRA_TARGET_PACKAGE, packageName)
                putExtra(EXTRA_TARGET_APP_NAME, appName)
                putExtra(EXTRA_IS_SYSTEM_APP, isSystemApp)
                putParcelableArrayListExtra(EXTRA_APK_URIS, uris)

                // Attach all APK URIs to ClipData so Android automatically propagates
                // cross-profile FLAG_GRANT_READ_URI_PERMISSION to User 10.
                if (uris.isNotEmpty()) {
                    val clip = ClipData.newRawUri("clone_apks", uris.first())
                    for (i in 1 until uris.size) {
                        clip.addItem(ClipData.Item(uris[i]))
                    }
                    clipData = clip
                }
            }
        }
    }

    /**
     * Builds a cross-profile Intent asking WorkCloneInstallerActivity inside User 10
     * to uninstall or disable a cloned native package from the Work Profile.
     */
    fun buildCrossProfileUninstallIntent(packageName: String, appName: String): Intent {
        return Intent(ACTION_WORK_UNINSTALL_APK).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_TARGET_PACKAGE, packageName)
            putExtra(EXTRA_TARGET_APP_NAME, appName)
        }
    }
}
