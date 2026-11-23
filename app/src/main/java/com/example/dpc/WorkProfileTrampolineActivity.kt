package com.example.dpc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.engine.WorkProfileManager

/**
 * Lightweight zero-flash trampoline launched by Home Screen pinned shortcuts for native
 * Work Profile clones. Immediately starts the isolated app inside User 10 (/data/user/10/)
 * via LauncherApps.startMainActivity(component, workUserHandle) and never falls back to
 * launching the personal profile app.
 */
class WorkProfileTrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra(WorkCloneBridge.EXTRA_TARGET_PACKAGE).orEmpty()
        val appName = intent.getStringExtra(WorkCloneBridge.EXTRA_TARGET_APP_NAME) ?: "Clone"

        if (targetPackage.isNotBlank()) {
            val manager = WorkProfileManager(applicationContext)
            val launched = manager.launchAppInWorkProfile(targetPackage)
            if (!launched) {
                Toast.makeText(
                    applicationContext,
                    "$appName Work Clone is not installed or Work Profile is paused.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        finish()
    }

    companion object {
        fun createLaunchIntent(context: Context, packageName: String, appName: String): Intent {
            return Intent(context, WorkProfileTrampolineActivity::class.java).apply {
                action = WorkCloneBridge.ACTION_LAUNCH_WORK_SHORTCUT
                putExtra(WorkCloneBridge.EXTRA_TARGET_PACKAGE, packageName)
                putExtra(WorkCloneBridge.EXTRA_TARGET_APP_NAME, appName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }
    }
}
