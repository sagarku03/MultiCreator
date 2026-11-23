package com.example.engine

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.webkit.WebView
import androidx.core.graphics.toColorInt
import com.example.data.DpcAdminReceiver
import com.example.dpc.WorkCloneBridge
import java.util.UUID

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val isClonedInWorkProfile: Boolean,
    val hasWebPortal: Boolean,
    val icon: Drawable?
)

data class DeviceHealthSnapshot(
    val currentSsaid: String,
    val isWorkProfileActive: Boolean,
    val isProfileOwner: Boolean,
    val isCrossProfileBridgeReady: Boolean,
    val supportsManagedProfiles: Boolean,
    val profileCount: Int,
    val currentStoragePartition: String,
    val expectedWorkPartition: String,
    val androidVersionLabel: String,
    val deviceModel: String,
    val webViewEngineVersion: String,
    val securityPatchLevel: String,
    val widevineSecurityLevel: String,
    val multiProfileWebViewSupported: Boolean,
    val documentStartShieldSupported: Boolean,
    val requestedWithStripSupported: Boolean,
    val activeChromiumProfiles: Int,
    val isolationScore: Int
)

sealed class CloneActionResult {
    data class ReadyToDispatchInstaller(val appName: String, val installerIntent: Intent) : CloneActionResult()
    data class ClonedDirectly(val appName: String) : CloneActionResult()
    data class NeedsWorkProfileSetup(val appName: String) : CloneActionResult()
    data class NeedsBridgeSync(val appName: String) : CloneActionResult()
    data class ForeignWorkProfile(val appName: String) : CloneActionResult()
    data class AlreadyCloned(val appName: String) : CloneActionResult()
    data class Error(val message: String) : CloneActionResult()
}

/**
 * Production Android Work Profile & Package Manager engine utilizing DevicePolicyManager,
 * UserManager, LauncherApps, and WorkCloneBridge to provision a managed Work Profile
 * (/data/user/10/ with an isolated SSAID), clone unmodified base + split APKs across
 * profile boundaries, and launch genuine cloned apps natively under the Work UserHandle.
 */
class WorkProfileManager(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    private val userManager: UserManager? =
        context.getSystemService(Context.USER_SERVICE) as? UserManager
    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    private val packageManager: PackageManager = context.packageManager

    /**
     * Returns true if a secondary managed Work Profile exists on this device or if MultiCreator
     * itself is currently executing as the Profile Owner inside a Work Profile.
     */
    fun checkIfWorkProfileActive(): Boolean {
        if (isProfileOwner()) return true
        val profiles = userManager?.userProfiles ?: emptyList()
        return profiles.size > 1
    }

    /**
     * Returns true if MultiCreator is currently the active Device Policy Controller (Profile Owner).
     */
    fun isProfileOwner(): Boolean {
        return runCatching {
            devicePolicyManager?.isProfileOwnerApp(context.packageName) == true
        }.getOrDefault(false)
    }

    /**
     * Returns true when Personal Profile MultiCreator can route cross-profile APK install intents
     * straight to MultiCreator's Profile Owner installer inside User 10.
     */
    fun isCrossProfileBridgeReady(): Boolean {
        if (isProfileOwner()) return true
        return WorkCloneBridge.isCrossProfileCloneRouteReady(context)
    }

    /**
     * Returns true if MultiCreator itself is installed inside the active Work Profile.
     */
    fun isSelfInstalledInWorkProfile(): Boolean {
        val workHandle = getWorkUserHandle() ?: return false
        return isAppInstalledInWorkProfile(context.packageName, workHandle)
    }

    /**
     * Opens MultiCreator's Work Profile copy so it can initialize cross-profile policies.
     */
    fun launchSelfInWorkProfile(): Boolean {
        return launchAppInWorkProfile(context.packageName)
    }

    /**
     * Checks whether this device hardware & ROM supports Android Managed Work Profiles.
     */
    fun isManagedProfileSupported(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
    }

    /**
     * Resolves the secondary Work Profile UserHandle when available.
     */
    fun getWorkUserHandle(): UserHandle? {
        val currentHandle = Process.myUserHandle()
        val profiles = userManager?.userProfiles ?: return null
        return profiles.firstOrNull { it != currentHandle }
    }

    /**
     * Builds the official Android system Intent to provision an isolated Managed Work Profile
     * backed by MultiCreator's DpcAdminReceiver.
     */
    fun provisionWorkProfile(): Intent {
        val adminComponent = DpcAdminReceiver.getComponentName(context)
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR, "#06B6D4".toColorInt())
            }
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        }
    }

    /**
     * Fetches all launchable installed applications from the personal profile using LauncherApps
     * (and PackageManager fallback) and cross-checks which packages are active in the Work Profile.
     */
    fun getInstalledApps(): List<InstalledAppItem> {
        val selfPackage = context.packageName
        val workHandle = getWorkUserHandle()
        val lApps = launcherApps
        val workPackages: Set<String> = if (workHandle != null && lApps != null) {
            runCatching {
                lApps.getActivityList(null, workHandle)
                    .map { it.applicationInfo.packageName }
                    .toSet()
            }.getOrDefault(emptySet())
        } else {
            emptySet()
        }

        val personalHandle = Process.myUserHandle()
        val seenPackages = HashSet<String>()
        val result = mutableListOf<InstalledAppItem>()

        // Primary discovery via LauncherApps (scoped to launchable user-facing apps)
        val launcherActivities = runCatching {
            launcherApps?.getActivityList(null, personalHandle)
        }.getOrNull()

        if (!launcherActivities.isNullOrEmpty()) {
            for (activityInfo in launcherActivities) {
                val appInfo = activityInfo.applicationInfo
                val pkg = appInfo.packageName
                if (pkg == selfPackage || !seenPackages.add(pkg)) continue

                val versionName = runCatching {
                    packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0"
                }.getOrDefault("1.0")
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val icon = runCatching { activityInfo.getBadgedIcon(0) }.getOrNull()
                    ?: runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()

                result.add(
                    InstalledAppItem(
                        packageName = pkg,
                        appName = activityInfo.label?.toString()?.ifBlank { pkg } ?: pkg,
                        versionName = versionName,
                        isSystemApp = isSystem,
                        isClonedInWorkProfile = if (isProfileOwner()) true else pkg in workPackages,
                        hasWebPortal = ContainerEngine.resolveWebPortalForPackage(pkg) != null,
                        icon = icon
                    )
                )
            }
        } else {
            // Fallback discovery via MAIN/LAUNCHER intent query
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = packageManager.queryIntentActivities(mainIntent, 0)
            for (resolveInfo in resolved) {
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
                val pkg = appInfo.packageName
                if (pkg == selfPackage || !seenPackages.add(pkg)) continue

                val versionName = runCatching {
                    packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0"
                }.getOrDefault("1.0")
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                result.add(
                    InstalledAppItem(
                        packageName = pkg,
                        appName = resolveInfo.loadLabel(packageManager).toString().ifBlank { pkg },
                        versionName = versionName,
                        isSystemApp = isSystem,
                        isClonedInWorkProfile = if (isProfileOwner()) true else pkg in workPackages,
                        hasWebPortal = ContainerEngine.resolveWebPortalForPackage(pkg) != null,
                        icon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                    )
                )
            }
        }

        // Sort: Cloned apps first, then user apps alphabetically, then system apps
        return result.sortedWith(
            compareByDescending<InstalledAppItem> { it.isClonedInWorkProfile }
                .thenBy { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Stages an installed app's unmodified base + split APKs and prepares the cross-profile
     * installer Intent to genuinely install the app into Android's Managed Work Profile (/data/user/10/).
     *
     * Never falls back to launching the personal profile app.
     */
    suspend fun prepareNativeWorkClone(packageName: String, appName: String): CloneActionResult {
        if (!checkIfWorkProfileActive()) {
            return CloneActionResult.NeedsWorkProfileSetup(appName)
        }

        // If MultiCreator itself is currently open INSIDE the Work Profile
        if (isProfileOwner()) {
            if (packageManager.getLaunchIntentForPackage(packageName) != null) {
                return CloneActionResult.AlreadyCloned(appName)
            }
            val dpm = devicePolicyManager
            if (dpm != null) {
                val enabledDirectly = runCatching {
                    dpm.enableSystemApp(DpcAdminReceiver.getComponentName(context), packageName)
                    packageManager.getLaunchIntentForPackage(packageName) != null
                }.getOrDefault(false)
                if (enabledDirectly) {
                    return CloneActionResult.ClonedDirectly(appName)
                }
            }
            return CloneActionResult.Error(
                "Switch to your Personal Profile copy of MultiCreator to clone new apps into this Work Vault."
            )
        }

        val workHandle = getWorkUserHandle()
        if (workHandle != null && isAppInstalledInWorkProfile(packageName, workHandle)) {
            return CloneActionResult.AlreadyCloned(appName)
        }

        // Verify that our cross-profile bridge to User 10's Profile Owner installer is active
        if (!isCrossProfileBridgeReady()) {
            return if (isSelfInstalledInWorkProfile()) {
                CloneActionResult.NeedsBridgeSync(appName)
            } else {
                CloneActionResult.ForeignWorkProfile(appName)
            }
        }

        // Stage unmodified base.apk + all split APKs into FileProvider cache and return cross-profile Intent
        return WorkCloneBridge.buildCrossProfileCloneIntent(context, packageName, appName)
            .fold(
                onSuccess = { installerIntent ->
                    CloneActionResult.ReadyToDispatchInstaller(appName, installerIntent)
                },
                onFailure = { err ->
                    CloneActionResult.Error(
                        err.localizedMessage ?: "Failed to stage APK files for $appName."
                    )
                }
            )
    }

    /**
     * Builds the cross-profile uninstall Intent to remove a cloned app from User 10.
     */
    fun buildUninstallWorkCloneIntent(packageName: String, appName: String): Intent? {
        if (!isCrossProfileBridgeReady()) return null
        return WorkCloneBridge.buildCrossProfileUninstallIntent(packageName, appName)
    }

    /**
     * Launches a cloned application natively under the isolated Android Work Profile UserHandle (/data/user/10/).
     * When running inside the Work Profile itself, launches directly in User 10.
     */
    fun launchAppInWorkProfile(packageName: String): Boolean {
        if (isProfileOwner()) {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }

        val workHandle = getWorkUserHandle() ?: return false
        val lApps = launcherApps ?: return false

        val activities = runCatching {
            lApps.getActivityList(packageName, workHandle)
        }.getOrNull().orEmpty()

        val targetComponent: ComponentName = activities.firstOrNull()?.componentName ?: return false
        return runCatching {
            lApps.startMainActivity(targetComponent, workHandle, null, null)
            true
        }.getOrDefault(false)
    }

    /**
     * Launches an installed application in the personal profile if the user wants to open it directly.
     */
    fun launchPersonalApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun isAppInstalledInWorkProfile(packageName: String, workHandle: UserHandle): Boolean {
        return runCatching {
            launcherApps?.getActivityList(packageName, workHandle)?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    /**
     * Collects real-time device isolation diagnostics, SSAID, storage partition path, and
     * WebView anti-detect engine details for the Device Health & Info tab.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceDiagnostics(activeWebContainersCount: Int): DeviceHealthSnapshot {
        val ssaid = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().ifBlank { "UNAVAILABLE" }

        val workActive = checkIfWorkProfileActive()
        val profileOwner = isProfileOwner()
        val bridgeReady = isCrossProfileBridgeReady()
        val supportsManaged = isManagedProfileSupported()
        val profiles = userManager?.userProfiles ?: emptyList()
        val currentPartition = context.filesDir.absolutePath
            .substringBefore("/${context.packageName}")
            .ifBlank { "/data/user/0" }

        val webViewVersion = runCatching {
            WebView.getCurrentWebViewPackage()?.let { pkg ->
                "${pkg.packageName.substringAfterLast('.')} ${pkg.versionName ?: ""}".trim()
            }
        }.getOrNull() ?: "Android System WebView"

        val widevineLevel = queryWidevineSecurityLevel()
        val stealthCaps = WebSandboxManager(context).getWebViewStealthCapabilities()

        var score = 30
        if (supportsManaged) score += 10
        if (workActive) score += 15
        if (bridgeReady) score += 10
        if (stealthCaps.multiProfileSupported) score += 15
        if (stealthCaps.documentStartScriptSupported) score += 10
        if (widevineLevel.startsWith("L1")) score += 5
        if (activeWebContainersCount > 0) score += 5

        return DeviceHealthSnapshot(
            currentSsaid = ssaid,
            isWorkProfileActive = workActive,
            isProfileOwner = profileOwner,
            isCrossProfileBridgeReady = bridgeReady,
            supportsManagedProfiles = supportsManaged,
            profileCount = profiles.size.coerceAtLeast(1),
            currentStoragePartition = currentPartition,
            expectedWorkPartition = if (workActive) "/data/user/10 (Active)" else "/data/user/10 (Not Activated)",
            androidVersionLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            webViewEngineVersion = webViewVersion,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH.ifBlank { "Verified" },
            widevineSecurityLevel = widevineLevel,
            multiProfileWebViewSupported = stealthCaps.multiProfileSupported,
            documentStartShieldSupported = stealthCaps.documentStartScriptSupported,
            requestedWithStripSupported = stealthCaps.requestedWithHeaderControlSupported,
            activeChromiumProfiles = stealthCaps.activeChromiumProfileCount,
            isolationScore = score.coerceAtMost(100)
        )
    }

    /**
     * Queries hardware Widevine DRM security level (L1 = Hardware TEE vs L3 = Software fallback),
     * a real signal evaluated by streaming and anti-fraud systems alongside Play Integrity.
     */
    private fun queryWidevineSecurityLevel(): String {
        val widevineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        return runCatching {
            val drm = MediaDrm(widevineUuid)
            try {
                val level = drm.getPropertyString("securityLevel").ifBlank { "Unknown" }
                if (level.equals("L1", ignoreCase = true)) "L1 (Hardware TEE Verified)" else "$level (Software)"
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    drm.close()
                } else {
                    @Suppress("DEPRECATION")
                    drm.release()
                }
            }
        }.getOrDefault("Unavailable")
    }

    /**
     * Opens Android's Google Advertising ID (AAID) privacy screen so users can reset or delete
     * their cross-app Advertising ID in 1 tap when breaking a device-ban loop.
     */
    fun openGoogleAdsSettings(): Boolean {
        val candidates = listOf(
            Intent("com.google.android.gms.settings.ADS_PRIVACY"),
            Intent("android.settings.PRIVACY_SETTINGS"),
            Intent(Settings.ACTION_PRIVACY_SETTINGS)
        )
        return launchFirstAvailableIntent(candidates)
    }

    /**
     * Opens Android Accounts / Work Profile settings so users can remove a stale or ghost Work Profile.
     */
    fun openAndroidAccountsSettings(): Boolean {
        return launchFirstAvailableIntent(
            listOf(
                Intent(Settings.ACTION_SYNC_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
        )
    }

    /**
     * Opens Android Security / Screen Lock settings for devices with ROM lock-screen provisioning bugs.
     */
    fun openAndroidSecuritySettings(): Boolean {
        return launchFirstAvailableIntent(
            listOf(
                Intent(Settings.ACTION_SECURITY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
        )
    }

    private fun launchFirstAvailableIntent(candidates: List<Intent>): Boolean {
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }
}
