package com.sconcept.mirrordash.kodi

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme
import kotlinx.coroutines.delay

// isActive only flips true once the pager has fully SETTLED on this page - i.e. any swipe that
// brought the user here is already finished - so this grace window is purely "swipe away again to
// escape before Kodi takes over," matching the landing card's own "swipe away normally to leave it
// in MirrorDash" copy below. Cancelled for free by LaunchedEffect(isActive, ...) restarting the
// instant isActive flips false, so a real departing swipe always wins the race.
private const val KODI_AUTO_LAUNCH_GRACE_MS = 700L

/**
 * Kodi is a separate Android app, so this page launches it rather than trying to embed it inside
 * MirrorDash. Auto-launch is edge-triggered on page activation, not on every resume, so backing
 * out to Home doesn't immediately bounce you straight back into Kodi.
 */
@Composable
fun KodiScreen(
    packageName: String,
    isActive: Boolean,
    autoLaunchOnOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = remember(context) { KodiAppLauncher(context) }
    val packageToLaunch = packageName.trim()
    val launchTarget by produceState<KodiLaunchTarget?>(initialValue = null, launcher, packageToLaunch) {
        value = launcher.resolve(packageToLaunch)
    }
    var hasAutoLaunchedForThisActivation by remember(packageToLaunch) { mutableStateOf(false) }

    LaunchedEffect(isActive, autoLaunchOnOpen, launchTarget) {
        if (!isActive) {
            hasAutoLaunchedForThisActivation = false
            return@LaunchedEffect
        }
        if (autoLaunchOnOpen && !hasAutoLaunchedForThisActivation) {
            delay(KODI_AUTO_LAUNCH_GRACE_MS)
            hasAutoLaunchedForThisActivation = launchTarget?.let(launcher::launch) == true
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val target = launchTarget
        when {
            packageToLaunch.isBlank() -> {
                CenteredMessage(
                    title = "Kodi package isn't set",
                    subtitle = "Add Kodi's Android package name in Settings before enabling auto-launch.",
                )
            }
            target == null -> {
                CenteredMessage(
                    title = "Kodi isn't installed",
                    subtitle = "MirrorDash looked for $packageToLaunch but couldn't find a launchable app with that package.",
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Kodi is ready",
                        style = MDTheme.type.sectionTitle,
                        color = MDTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "This tab opens the installed Kodi app ($packageToLaunch). Swipe away from the tab normally to leave it in MirrorDash.",
                        style = MDTheme.type.body,
                        color = MDTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            launcher.launch(target)
                            hasAutoLaunchedForThisActivation = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MDTheme.colors.accent,
                            contentColor = MDTheme.colors.onAccent,
                        ),
                    ) {
                        Text("Open Kodi")
                    }
                }
            }
        }
    }
}

private data class KodiLaunchTarget(
    val packageName: String,
    val componentName: android.content.ComponentName?,
    val fallbackIntent: Intent?,
)

private class KodiAppLauncher(private val context: Context) {
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    fun resolve(packageName: String): KodiLaunchTarget? {
        if (packageName.isBlank()) return null
        val activity = launcherApps.getActivityList(packageName, Process.myUserHandle()).firstOrNull()
        if (activity != null) {
            return KodiLaunchTarget(
                packageName = packageName,
                componentName = activity.componentName,
                fallbackIntent = null,
            )
        }
        val standardIntent = packageManager.getLaunchIntentForPackage(packageName)
        val leanbackIntent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
        val fallback = standardIntent ?: leanbackIntent
        return fallback?.let {
            KodiLaunchTarget(
                packageName = packageName,
                componentName = null,
                fallbackIntent = it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun launch(target: KodiLaunchTarget): Boolean = runCatching {
        target.componentName?.let { component ->
            launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
        } ?: context.startActivity(target.fallbackIntent)
        true
    }.getOrDefault(false)
}

@Composable
private fun CenteredMessage(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MDTheme.type.sectionTitle,
            color = MDTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = subtitle,
            style = MDTheme.type.body,
            color = MDTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
