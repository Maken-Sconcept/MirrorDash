package com.sconcept.mirrordash.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.sconcept.mirrordash.settings.BROWSER_EMPTY_START_PAGE_URL
import com.sconcept.mirrordash.ui.theme.MDTheme
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    homeUrl: String,
    persistedUrl: String,
    onPersistCurrentUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val initialUrl = remember(homeUrl, persistedUrl) {
        persistedUrl.takeIf { it.isNotBlank() } ?: normalizeStartPage(homeUrl)
    }

    var currentUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var addressDraft by rememberSaveable(initialUrl) { mutableStateOf(if (initialUrl == BROWSER_EMPTY_START_PAGE_URL) "" else initialUrl) }
    var pageTitle by rememberSaveable { mutableStateOf("Web") }
    var loadError by rememberSaveable { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableIntStateOf(100) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }

    DisposableEffect(Unit) {
        onDispose {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            fullscreenCallback?.onCustomViewHidden()
        }
    }

    DisposableEffect(lifecycleOwner, webViewRef) {
        val webView = webViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = fullscreenView != null || canGoBack) {
        when {
            fullscreenView != null -> {
                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = null
                fullscreenCallback = null
            }
            canGoBack -> webViewRef?.goBack()
        }
    }

    val chromeClient = remember(context, fileChooserLauncher) {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadProgress = newProgress
                isLoading = newProgress < 100
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    pageTitle = title
                }
            }

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

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                val callback = filePathCallback ?: return false
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                val intent = runCatching { fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT) }.getOrNull()
                if (intent == null) {
                    fileChooserCallback = null
                    callback.onReceiveValue(null)
                    return false
                }
                return runCatching {
                    fileChooserLauncher.launch(intent)
                    true
                }.getOrElse {
                    fileChooserCallback = null
                    callback.onReceiveValue(null)
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val approved = mutableListOf<String>()
                if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                ) {
                    approved += PermissionRequest.RESOURCE_AUDIO_CAPTURE
                }
                if (approved.isNotEmpty()) {
                    request.grant(approved.toTypedArray())
                } else {
                    request.deny()
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                val parent = webViewRef ?: return false
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = WebView(parent.context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url?.toString().orEmpty()
                            if (target.isNotBlank()) {
                                parent.loadUrl(target)
                            }
                            destroy()
                            return true
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (!url.isNullOrBlank()) {
                                parent.loadUrl(url)
                            }
                            destroy()
                        }
                    }
                }
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    val client = remember(context, onPersistCurrentUrl) {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return false
                return handleCustomScheme(context, target)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadError = null
                isLoading = true
                syncNavigationState(view) { back, forward ->
                    canGoBack = back
                    canGoForward = forward
                }
                if (!url.isNullOrBlank()) {
                    currentUrl = url
                    addressDraft = url
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isLoading = false
                syncNavigationState(view) { back, forward ->
                    canGoBack = back
                    canGoForward = forward
                }
                if (!url.isNullOrBlank()) {
                    currentUrl = url
                    addressDraft = url
                    onPersistCurrentUrl(url)
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == false) return
                loadError = error?.description?.toString() ?: "Couldn't load that page."
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == false) return
                loadError = "This page returned HTTP ${errorResponse?.statusCode ?: "error"}."
            }
        }
    }

    fun loadFromAddressBar() {
        val target = normalizeUserInput(addressDraft, homeUrl)
        keyboardController?.hide()
        if (!handleCustomScheme(context, Uri.parse(target))) {
            webViewRef?.loadUrl(target)
        }
    }

    LaunchedEffect(initialUrl) {
        currentUrl = initialUrl
        if (initialUrl == BROWSER_EMPTY_START_PAGE_URL) {
            addressDraft = ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MDTheme.colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MDTheme.colors.surfaceElevated,
                contentColor = MDTheme.colors.textPrimary,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = MDTheme.colors.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                            }
                        }
                        TextField(
                            value = addressDraft,
                            onValueChange = {
                                addressDraft = it
                                loadError = null
                            },
                            placeholder = { Text("Search or enter address") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MDTheme.colors.textSecondary,
                                )
                            },
                            textStyle = MDTheme.type.body.copy(color = MDTheme.colors.textPrimary),
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { loadFromAddressBar() }),
                            shape = RoundedCornerShape(18.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MDTheme.colors.surface,
                                unfocusedContainerColor = MDTheme.colors.surface,
                                focusedTextColor = MDTheme.colors.textPrimary,
                                unfocusedTextColor = MDTheme.colors.textPrimary,
                                focusedIndicatorColor = MDTheme.colors.accent,
                                unfocusedIndicatorColor = MDTheme.colors.divider,
                                cursorColor = MDTheme.colors.accent,
                                focusedPlaceholderColor = MDTheme.colors.textTertiary,
                                unfocusedPlaceholderColor = MDTheme.colors.textTertiary,
                                focusedLeadingIconColor = MDTheme.colors.textSecondary,
                                unfocusedLeadingIconColor = MDTheme.colors.textSecondary,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { webViewRef?.loadUrl(normalizeStartPage(homeUrl)) }) {
                            Icon(Icons.Filled.Home, contentDescription = "Home")
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                    ) {
                        Text(
                            text = pageTitle,
                            style = MDTheme.type.settingSubtitle,
                            color = MDTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = prettyHost(currentUrl),
                            style = MDTheme.type.caption,
                            color = MDTheme.colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isLoading) {
                        LinearProgressIndicator(
                            progress = { (loadProgress.coerceIn(0, 100) / 100f) },
                            color = MDTheme.colors.accent,
                            trackColor = MDTheme.colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(AndroidColor.BLACK)
                            settings.applyBrowserDefaults()
                            webChromeClient = chromeClient
                            webViewClient = client
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                enqueueDownload(
                                    context = viewContext,
                                    url = url,
                                    userAgent = userAgent,
                                    contentDisposition = contentDisposition,
                                    mimeType = mimeType,
                                )
                            }
                            setOnTouchListener { _, motionEvent ->
                                when (motionEvent.actionMasked) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(true)
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
                                }
                                false
                            }
                            webViewRef = this
                            if (initialUrl != BROWSER_EMPTY_START_PAGE_URL) {
                                loadUrl(initialUrl)
                            } else {
                                loadUrl(BROWSER_EMPTY_START_PAGE_URL)
                            }
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        syncNavigationState(webView) { back, forward ->
                            canGoBack = back
                            canGoForward = forward
                        }
                    },
                    onRelease = {
                        webViewRef = null
                        it.stopLoading()
                        it.webChromeClient = null
                        it.destroy()
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (currentUrl == BROWSER_EMPTY_START_PAGE_URL && !isLoading && fullscreenView == null) {
                    EmptyBrowserState(homeUrl = homeUrl)
                }

                if (loadError != null && fullscreenView == null) {
                    Surface(
                        color = MDTheme.colors.surfaceElevated.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp, start = 18.dp, end = 18.dp),
                    ) {
                        Text(
                            text = loadError ?: "",
                            style = MDTheme.type.settingSubtitle,
                            color = MDTheme.colors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }

        if (fullscreenView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black),
            ) {
                AndroidView(
                    factory = { fullscreenView!!.parent?.let { parent -> (parent as? ViewGroup)?.removeView(fullscreenView) }; fullscreenView!! },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun EmptyBrowserState(homeUrl: String) {
    val subtitle = if (homeUrl.isBlank()) {
        "Type a URL or search term in the bar above to start a quick session."
    } else {
        "Use the bar above, or tap Home to jump to your configured start page."
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Text("Browser tab ready", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun WebSettings.applyBrowserDefaults() {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    useWideViewPort = true
    loadWithOverviewMode = true
    builtInZoomControls = true
    displayZoomControls = false
    setSupportZoom(true)
    javaScriptCanOpenWindowsAutomatically = true
    setSupportMultipleWindows(true)
    mediaPlaybackRequiresUserGesture = false
    allowFileAccess = true
    allowContentAccess = true
    cacheMode = WebSettings.LOAD_DEFAULT
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, true)
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
        WebSettingsCompat.setBackForwardCacheEnabled(this, true)
    }
}

private fun normalizeStartPage(homeUrl: String): String {
    val trimmed = homeUrl.trim()
    if (trimmed.isBlank()) return BROWSER_EMPTY_START_PAGE_URL
    return if (trimmed.contains("://")) trimmed else "https://$trimmed"
}

private fun normalizeUserInput(rawInput: String, homeUrl: String): String {
    val input = rawInput.trim()
    if (input.isBlank()) return normalizeStartPage(homeUrl)
    if (input == "about:blank") return input
    if (input.contains("://")) return input
    if (input.startsWith("mailto:") || input.startsWith("tel:")) return input
    val looksLikeHost = !input.contains(' ') && (input.contains('.') || input.equals("localhost", ignoreCase = true))
    return if (looksLikeHost) {
        "https://$input"
    } else {
        val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
        "https://www.google.com/search?q=$encoded"
    }
}

private fun prettyHost(url: String): String {
    if (url == BROWSER_EMPTY_START_PAGE_URL) return "Ready"
    return Uri.parse(url).host ?: url
}

private fun syncNavigationState(
    webView: WebView?,
    onUpdate: (canGoBack: Boolean, canGoForward: Boolean) -> Unit = { _, _ -> },
) {
    onUpdate(webView?.canGoBack() == true, webView?.canGoForward() == true)
}

private fun handleCustomScheme(context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme.orEmpty()
    if (scheme == "http" || scheme == "https" || scheme == "about") return false
    return when (scheme) {
        "intent" -> openIntentUri(context, uri.toString())
        else -> openExternalIntent(context, Intent(Intent.ACTION_VIEW, uri))
    }
}

private fun openIntentUri(context: Context, uri: String): Boolean {
    val parsed = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return false
    if (openExternalIntent(context, parsed)) return true
    return parsed.getStringExtra("browser_fallback_url")?.let { fallback ->
        openExternalIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
    } ?: false
}

private fun openExternalIntent(context: Context, intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse { false }
}

private fun enqueueDownload(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
) {
    val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setMimeType(mimeType)
        setTitle(guessedName)
        setDescription(Uri.parse(url).host ?: url)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessedName)
        setAllowedOverMetered(true)
        setAllowedOverRoaming(true)
        userAgent?.let { addRequestHeader("User-Agent", it) }
        CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
    }
    val manager = ContextCompat.getSystemService(context, DownloadManager::class.java)
    if (manager != null) {
        manager.enqueue(request)
        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Couldn't start download manager", Toast.LENGTH_SHORT).show()
    }
}
