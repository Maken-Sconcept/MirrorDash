package com.sconcept.mirrordash.homeassistant

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * Full-bleed kiosk view of a Home Assistant dashboard - a plain [WebView] rather than launching
 * the Home Assistant app, since Android has no supported way to host another app's Activity
 * inside this one's window (the alternative, an accessibility-overlay screen-mirror, is exactly
 * the kind of fragile hack worth avoiding). This is also how the official Home Assistant
 * companion app and dedicated wall-panel browsers (Fully Kiosk, etc.) work under the hood - the
 * dashboard URL already *is* the app.
 */
@Composable
fun HomeAssistantScreen(url: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(MDTheme.colors.background)) {
        if (url.isBlank()) {
            NotConfiguredState()
        } else {
            KioskWebView(url = url)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KioskWebView(url: String) {
    var loadError by remember(url) { mutableStateOf<String?>(null) }
    var reloadToken by remember(url) { mutableStateOf(0) }

    if (loadError != null) {
        ErrorState(message = loadError ?: "Couldn't load the dashboard", onRetry = { loadError = null; reloadToken++ })
        return
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame != false) {
                            loadError = error?.description?.toString() ?: "Couldn't reach that address"
                        }
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != url || webView.getTag(TAG_RELOAD) != reloadToken) {
                webView.tag = url
                webView.setTag(TAG_RELOAD, reloadToken)
                webView.loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
}

private val TAG_RELOAD = "ha_reload_token".hashCode()

@Composable
private fun NotConfiguredState() {
    CenteredMessage(
        title = "Home Assistant isn't set up yet",
        subtitle = "Add your dashboard address in Settings to turn this page into a live panel.",
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Can't reach Home Assistant", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun CenteredMessage(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
    }
}
