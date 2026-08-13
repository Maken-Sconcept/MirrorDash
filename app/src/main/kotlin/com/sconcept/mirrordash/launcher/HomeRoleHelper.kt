package com.sconcept.mirrordash.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Becoming the default Home app looks different depending on API level (brief section 4: "never
 * bypassed" - always the real system picker, just reached differently):
 * - API 29+ (`RoleManager`, `ROLE_HOME`) is the modern, precise mechanism.
 * - Below that (including this project's real API 25 target hardware) there is no RoleManager;
 *   the standard path is opening `Settings.ACTION_HOME_SETTINGS`, which shows Android's own
 *   "Home app" chooser.
 */
object HomeRoleHelper {

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun requestHomeRoleIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            } else {
                Intent(Settings.ACTION_HOME_SETTINGS)
            }
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    }
}
