package com.example.dpc

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.MainActivity
import com.example.data.DpcAdminReceiver

/**
 * Mandatory Android 12+ (API 31+) DPC handler for ACTION_GET_PROVISIONING_MODE.
 * Tells Android's Managed Provisioning wizard that MultiCreator requests an isolated
 * Managed Work Profile (/data/user/10/) without resetting the device.
 */
class ProvisioningModeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resultIntent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val allowedModes = intent?.getIntegerArrayListExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
            )
            val chosenMode = if (
                allowedModes == null ||
                allowedModes.contains(DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE)
            ) {
                DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            } else {
                allowedModes.firstOrNull() ?: DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            }

            resultIntent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, chosenMode)
            @Suppress("DEPRECATION")
            val adminBundle = intent?.getBundleExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE
            )
            if (adminBundle != null) {
                resultIntent.putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                    adminBundle
                )
            }
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

/**
 * Mandatory Android 12+ (API 31+) DPC handler for ACTION_ADMIN_POLICY_COMPLIANCE and
 * ACTION_PROVISIONING_SUCCESSFUL.
 *
 * IMPORTANT: During ACTION_ADMIN_POLICY_COMPLIANCE, Android ManagedProvisioning expects
 * this activity to return RESULT_OK and finish cleanly without hijacking the task stack
 * via startActivity(), otherwise SetupWizard / FinalizationActivity can abort with
 * "Can't set up device".
 */
class PolicyComplianceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Signal compliance immediately so any non-fatal policy warning never aborts setup
        setResult(RESULT_OK)

        runCatching {
            WorkCloneBridge.configureWorkProfilePolicies(applicationContext)
        }

        val isPostSetupSuccess =
            intent?.action == DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL

        finish()

        // Only launch MainActivity when invoked via post-setup PROVISIONING_SUCCESSFUL,
        // never during ADMIN_POLICY_COMPLIANCE (where ManagedProvisioning owns the flow).
        if (isPostSetupSuccess) {
            runCatching {
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(DpcAdminReceiver.EXTRA_PROVISIONING_JUST_COMPLETED, true)
                }
                startActivity(launchIntent)
            }
        }
    }
}
