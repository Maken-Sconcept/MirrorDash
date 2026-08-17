package com.sconcept.mirrordash.jellyfin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sconcept.mirrordash.launcher.gestures.swipePriorityTouchListener
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * Full-screen Jellyfin surface embedded as a WebView. Unlike IPTV, this intentionally reuses the
 * server's own web UI instead of rebuilding library/auth/playback flows natively inside
 * MirrorDash.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JellyfinScreen(
    serverUrl: String,
    startPath: String,
    desktopMode: Boolean,
    reloadOnOpen: Boolean,
    openExternalLinks: Boolean,
    isActive: Boolean,
    isAwake: Boolean,
    modifier: Modifier = Modifier,
) {
    val normalizedServerUrl = remember(serverUrl) { normalizeBaseUrl(serverUrl) }
    val initialUrl = remember(normalizedServerUrl, startPath) { buildStartUrl(normalizedServerUrl, startPath) }
    val serverHost = remember(normalizedServerUrl) { normalizedServerUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it).host } }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (normalizedServerUrl.isBlank()) {
            CenteredMessage(
                title = "Jellyfin isn't set up yet",
                subtitle = "Add your server address in Settings to turn this page into your media library.",
            )
        } else {
            JellyfinWebView(
                initialUrl = initialUrl,
                serverHost = serverHost,
                desktopMode = desktopMode,
                reloadOnOpen = reloadOnOpen,
                openExternalLinks = openExternalLinks,
                isActive = isActive,
                isAwake = isAwake,
            )
        }
    }
}

@Composable
private fun JellyfinWebView(
    initialUrl: String,
    serverHost: String?,
    desktopMode: Boolean,
    reloadOnOpen: Boolean,
    openExternalLinks: Boolean,
    isActive: Boolean,
    isAwake: Boolean,
) {
    // A plain captured Boolean would freeze at whatever isAwake was when the WebView's factory
    // lambda ran (once, ever) - the touch listener needs the CURRENT value on every touch, so it
    // reads through this State instead, the same way `update = {}` keeps other WebView state
    // fresh across recompositions without recreating the View.
    val isAwakeState = rememberUpdatedState(isAwake)
    var loadError by remember(initialUrl, desktopMode, openExternalLinks) { mutableStateOf<String?>(null) }
    var reloadToken by remember(initialUrl, desktopMode, openExternalLinks) { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var hasSeenActive by remember { mutableStateOf(false) }

    BackHandler(enabled = fullscreenView != null) {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
    }

    DisposableEffect(Unit) {
        onDispose {
            fullscreenCallback?.onCustomViewHidden()
            fullscreenCallback = null
            fullscreenView = null
        }
    }

    LaunchedEffect(isActive, reloadOnOpen, webViewRef) {
        if (!isActive) return@LaunchedEffect
        if (hasSeenActive && reloadOnOpen) {
            webViewRef?.reload()
        }
        hasSeenActive = true
    }

    if (loadError != null) {
        ErrorState(message = loadError ?: "Couldn't load Jellyfin", onRetry = {
            loadError = null
            reloadToken++
        })
        return
    }

    val chromeClient = remember {
        object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                fullscreenView = view
                fullscreenCallback = callback
            }

            override fun onHideCustomView() {
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
            }
        }
    }

    val webClient = remember(serverHost, openExternalLinks) {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return false
                if (target.scheme != "http" && target.scheme != "https") {
                    return openExternalIntent(view?.context, Intent(Intent.ACTION_VIEW, target))
                }
                return if (openExternalLinks && shouldOpenOutsideServer(target, serverHost)) {
                    openExternalIntent(view?.context, Intent(Intent.ACTION_VIEW, target))
                } else {
                    false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadError = null
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == false) return
                loadError = error?.description?.toString() ?: "Couldn't reach that Jellyfin server."
            }
        }
    }

    key(initialUrl, desktopMode, openExternalLinks, reloadToken) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(AndroidColor.BLACK)
                        settings.applyJellyfinDefaults(desktopMode)
                        webChromeClient = chromeClient
                        webViewClient = webClient
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        setOnTouchListener(swipePriorityTouchListener(context, isAwakeState))
                        webViewRef = this
                        loadUrl(initialUrl)
                    }
                },
                update = { webViewRef = it },
                onRelease = {
                    webViewRef = null
                    it.stopLoading()
                    it.webChromeClient = null
                    it.destroy()
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (fullscreenView != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    AndroidView(
                        factory = {
                            fullscreenView!!.parent?.let { parent -> (parent as? ViewGroup)?.removeView(fullscreenView) }
                            fullscreenView!!
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun WebSettings.applyJellyfinDefaults(desktopMode: Boolean) {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    useWideViewPort = true
    loadWithOverviewMode = true
    builtInZoomControls = false
    displayZoomControls = false
    setSupportZoom(true)
    javaScriptCanOpenWindowsAutomatically = true
    setSupportMultipleWindows(false)
    mediaPlaybackRequiresUserGesture = false
    allowFileAccess = true
    allowContentAccess = true
    cacheMode = WebSettings.LOAD_DEFAULT
    if (desktopMode) {
        val currentAgent = userAgentString.orEmpty()
        userAgentString = currentAgent
            .replace("Mobile", "eliboM", ignoreCase = false)
            .replace("Android", "diordnA", ignoreCase = false)
    }
}

private fun normalizeBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""
    return if (trimmed.contains("://")) trimmed else "http://$trimmed"
}

private fun buildStartUrl(baseUrl: String, startPath: String): String {
    if (baseUrl.isBlank()) return ""
    val trimmedPath = startPath.trim()
    if (trimmedPath.isBlank()) return baseUrl
    return if (trimmedPath.startsWith("/")) "$baseUrl$trimmedPath" else "$baseUrl/$trimmedPath"
}

private fun shouldOpenOutsideServer(target: Uri, serverHost: String?): Boolean {
    if (serverHost.isNullOrBlank()) return false
    return target.host?.equals(serverHost, ignoreCase = true) == false
}

private fun openExternalIntent(context: Context?, intent: Intent): Boolean {
    if (context == null) return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse { false }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Can't reach Jellyfin", style = MDTheme.type.settingTitle, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MDTheme.type.settingSubtitle,
            color = Color.White.copy(alpha = 0.7f),
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
        Text(title, style = MDTheme.type.settingTitle, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MDTheme.type.settingSubtitle,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
    }
}
