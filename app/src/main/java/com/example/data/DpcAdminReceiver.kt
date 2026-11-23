package com.example.data

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity
import com.example.dpc.WorkCloneBridge

/**
 * Device Policy Controller (DPC) receiver managing Android Work Profile lifecycle events.
 * Enables the isolated managed profile partition (/data/user/10/) with an independent SSAID
 * and configures the cross-profile APK cloning bridge.
 */
class DpcAdminReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        val configured = WorkCloneBridge.configureWorkProfilePolicies(context)
        if (!configured) {
            val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val componentName = getComponentName(context)
            runCatching {
                manager?.setProfileName(componentName, PROFILE_DISPLAY_NAME)
                manager?.setProfileEnabled(componentName)
            }.onFailure { securityException ->
                Log.e(TAG, "SecurityException enabling managed profile", securityException)
            }
        }

        // Launch MultiCreator inside the newly provisioned profile to finalize setup
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_PROVISIONING_JUST_COMPLETED, true)
        }
        runCatching { context.startActivity(launchIntent) }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "MultiCreator Device Admin enabled.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "MultiCreator Device Admin disabled.")
    }

    companion object {
        private const val TAG = "DpcAdminReceiver"
        const val PROFILE_DISPLAY_NAME = "MultiCreator Vault"
        const val EXTRA_PROVISIONING_JUST_COMPLETED = "extra_provisioning_just_completed"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, DpcAdminReceiver::class.java)
        }
    }
}
