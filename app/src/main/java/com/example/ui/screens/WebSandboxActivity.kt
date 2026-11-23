package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.ContainerStore
import com.example.data.UserAgentMode
import com.example.data.WebContainerProfile
import com.example.engine.ShortcutCreator
import com.example.engine.ShortcutPinResult
import com.example.engine.WebSandboxManager
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassBadge
import com.example.ui.theme.GlassBorderMedium
import com.example.ui.theme.GlassIconButton
import com.example.ui.theme.GlassSurfaceFill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MultiCreatorTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBright
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianMid
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Isolated Anti-Detect Web Sandbox Activity hosting a hardened WebView with independent
 * cookie-jar snapshotting, live User-Agent switching, WebRTC IP-leak blocking, and
 * 1-click Home Screen shortcut pinning.
 */
class WebSandboxActivity : ComponentActivity() {

    private lateinit var sandboxManager: WebSandboxManager
    private lateinit var shortcutCreator: ShortcutCreator
    private val trackedOrigins = linkedSetOf<String>()
    private var activeContainerId: String = "default_stealth"
    private var pendingFileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileUploadLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val callback = pendingFileUploadCallback
        pendingFileUploadCallback = null
        callback?.onReceiveValue(if (uris.isNullOrEmpty()) null else uris.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        sandboxManager = WebSandboxManager(applicationContext)
        shortcutCreator = ShortcutCreator(applicationContext)

        val container = parseContainerFromIntent(intent)
        activeContainerId = container.id
        sandboxManager.extractOrigin(container.url)?.let { trackedOrigins.add(it) }

        setContent {
            MultiCreatorTheme {
                WebSandboxScreen(
                    container = container,
                    sandboxManager = sandboxManager,
                    onOriginVisited = { origin -> trackedOrigins.add(origin) },
                    onPinShortcut = { updatedContainer ->
                        shortcutCreator.pinWebSandboxShortcut(updatedContainer)
                    },
                    onPersistCookies = {
                        sandboxManager.persistContainerCookieJar(activeContainerId, trackedOrigins)
                    },
                    onOpenFileChooser = { callback, mimeTypes ->
                        pendingFileUploadCallback?.onReceiveValue(null)
                        pendingFileUploadCallback = callback
                        val accepted = mimeTypes.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }
                        runCatching {
                            fileUploadLauncher.launch(accepted.toTypedArray())
                            true
                        }.getOrElse {
                            pendingFileUploadCallback = null
                            callback.onReceiveValue(null)
                            false
                        }
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sandboxManager.persistContainerCookieJar(activeContainerId, trackedOrigins)
    }

    override fun onDestroy() {
        sandboxManager.persistContainerCookieJar(activeContainerId, trackedOrigins)
        super.onDestroy()
    }

    private fun parseContainerFromIntent(intent: Intent): WebContainerProfile {
        val id = intent.getStringExtra(EXTRA_CONTAINER_ID) ?: "stealth_ephemeral"
        val name = intent.getStringExtra(EXTRA_CONTAINER_NAME) ?: "Stealth Sandbox"
        val url = intent.getStringExtra(EXTRA_CONTAINER_URL) ?: "https://www.instagram.com/"
        val tag = intent.getStringExtra(EXTRA_PLATFORM_TAG) ?: "Web"
        val uaMode = UserAgentMode.fromNameOrDefault(intent.getStringExtra(EXTRA_UA_MODE))
        val seed = intent.getStringExtra(EXTRA_FINGERPRINT_SEED) ?: ContainerStore.generateFingerprintSeed()
        val blockWebRtc = intent.getBooleanExtra(EXTRA_BLOCK_WEBRTC, true)
        val accentHex = intent.getLongExtra(EXTRA_ACCENT_HEX, 0xFF06B6D4L)
        return WebContainerProfile(
            id = id,
            name = name,
            url = ContainerStore.normalizeHttpsUrl(url),
            platformTag = tag,
            userAgentMode = uaMode,
            fingerprintSeed = seed,
            blockWebRtc = blockWebRtc,
            accentHex = accentHex
        )
    }

    companion object {
        const val ACTION_LAUNCH_WEB_SANDBOX = "com.example.action.LAUNCH_WEB_SANDBOX"
        const val EXTRA_CONTAINER_ID = "extra_container_id"
        const val EXTRA_CONTAINER_NAME = "extra_container_name"
        const val EXTRA_CONTAINER_URL = "extra_container_url"
        const val EXTRA_PLATFORM_TAG = "extra_platform_tag"
        const val EXTRA_UA_MODE = "extra_ua_mode"
        const val EXTRA_FINGERPRINT_SEED = "extra_fingerprint_seed"
        const val EXTRA_BLOCK_WEBRTC = "extra_block_webrtc"
        const val EXTRA_ACCENT_HEX = "extra_accent_hex"

        fun createLaunchIntent(context: Context, container: WebContainerProfile): Intent {
            return Intent(context, WebSandboxActivity::class.java).apply {
                action = ACTION_LAUNCH_WEB_SANDBOX
                putExtra(EXTRA_CONTAINER_ID, container.id)
                putExtra(EXTRA_CONTAINER_NAME, container.name)
                putExtra(EXTRA_CONTAINER_URL, container.url)
                putExtra(EXTRA_PLATFORM_TAG, container.platformTag)
                putExtra(EXTRA_UA_MODE, container.userAgentMode.name)
                putExtra(EXTRA_FINGERPRINT_SEED, container.fingerprintSeed)
                putExtra(EXTRA_BLOCK_WEBRTC, container.blockWebRtc)
                putExtra(EXTRA_ACCENT_HEX, container.accentHex)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

@Composable
private fun WebSandboxScreen(
    container: WebContainerProfile,
    sandboxManager: WebSandboxManager,
    onOriginVisited: (String) -> Unit,
    onPinShortcut: (WebContainerProfile) -> ShortcutPinResult,
    onPersistCookies: () -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>, List<String>) -> Boolean,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf(container.url) }
    var urlInput by remember { mutableStateOf(container.url) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var activeUaMode by remember { mutableStateOf(container.userAgentMode) }
    var activeUaString by remember {
        mutableStateOf(sandboxManager.resolveUserAgent(container.userAgentMode, container.fingerprintSeed))
    }
    var showUaMenu by remember { mutableStateOf(false) }
    var showShieldDialog by remember { mutableStateOf(false) }
    var cookieJarReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(container.id) {
        sandboxManager.activateContainerCookieJar(container.id) {
            cookieJarReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onPersistCookies()
            webViewRef?.destroy()
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onPersistCookies()
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Frosted Top Control Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ObsidianDeep, ObsidianMid)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        onClick = {
                            onPersistCookies()
                            onClose()
                        },
                        modifier = Modifier.testTag("sandbox_close_button")
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = container.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GlassBadge(
                                label = activeUaMode.badgeLabel,
                                color = NeonCyanBright
                            )
                            if (container.blockWebRtc) {
                                GlassBadge(
                                    label = "WebRTC Off",
                                    color = EmeraldGlow
                                )
                            }
                        }
                    }

                    // User-Agent Switcher Menu
                    Box {
                        GlassIconButton(
                            icon = Icons.Rounded.Devices,
                            contentDescription = stringResource(R.string.sandbox_switch_ua),
                            tint = NeonCyanBright,
                            onClick = { showUaMenu = true },
                            modifier = Modifier.testTag("sandbox_ua_switch_button")
                        )
                        DropdownMenu(
                            expanded = showUaMenu,
                            onDismissRequest = { showUaMenu = false },
                            containerColor = ObsidianElevated
                        ) {
                            UserAgentMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (mode == activeUaMode) "✓ ${mode.displayName}" else mode.displayName,
                                            color = if (mode == activeUaMode) NeonCyanBright else TextPrimary
                                        )
                                    },
                                    onClick = {
                                        showUaMenu = false
                                        activeUaMode = mode
                                        val nextUa = sandboxManager.resolveUserAgent(mode, container.fingerprintSeed)
                                        activeUaString = nextUa
                                        webViewRef?.let { wv ->
                                            sandboxManager.reapplyUserAgentForContainer(
                                                webView = wv,
                                                containerId = container.id,
                                                mode = mode,
                                                fingerprintSeed = container.fingerprintSeed
                                            )
                                            wv.reload()
                                        }
                                        scope.launch {
                                            snackbarHostState.showSnackbar("User-Agent switched to ${mode.displayName}")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Clear Container Cookies
                    GlassIconButton(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.sandbox_clear_cookies),
                        tint = RoseAlert,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            sandboxManager.clearContainerSession(container.id, webViewRef) {
                                webViewRef?.loadUrl(container.url)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Cleared isolated cookie jar & cache.")
                                }
                            }
                        },
                        modifier = Modifier.testTag("sandbox_clear_cookies_button")
                    )

                    // Pin Web Shortcut to Home
                    GlassIconButton(
                        icon = Icons.Rounded.AddToHomeScreen,
                        contentDescription = stringResource(R.string.sandbox_pin_shortcut),
                        tint = EmeraldGlow,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val updated = container.copy(url = currentUrl, userAgentMode = activeUaMode)
                            val msg = when (val res = onPinShortcut(updated)) {
                                is ShortcutPinResult.Pinned -> "Pinned \"${res.label}\" to Home Screen."
                                is ShortcutPinResult.Unsupported -> res.reason
                                is ShortcutPinResult.Error -> res.message
                            }
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                        modifier = Modifier.testTag("sandbox_pin_shortcut_button")
                    )
                }

                // Progress indicator
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        color = NeonCyanBright,
                        trackColor = GlassSurfaceFill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50.dp))
                    )
                }
            }

            // Isolated WebView Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ObsidianDeep)
            ) {
                if (cookieJarReady) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                sandboxManager.attachIsolatedProfileAndShield(
                                    webView = this,
                                    containerId = container.id,
                                    mode = activeUaMode,
                                    fingerprintSeed = container.fingerprintSeed,
                                    blockWebRtc = container.blockWebRtc
                                )

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                    }

                                    override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri>>?,
                                        fileChooserParams: FileChooserParams?
                                    ): Boolean {
                                        if (filePathCallback == null) return false
                                        val mimes = fileChooserParams?.acceptTypes?.toList().orEmpty()
                                        return onOpenFileChooser(filePathCallback, mimes)
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val target = request?.url?.toString() ?: return false
                                        // Keep web browsing strictly inside the isolated sandbox
                                        return !(target.startsWith("https://") || target.startsWith("http://"))
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (!url.isNullOrBlank()) {
                                            currentUrl = url
                                            urlInput = url
                                            sandboxManager.extractOrigin(url)?.let(onOriginVisited)
                                        }
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                        view?.evaluateJavascript(
                                            sandboxManager.buildAntiDetectShieldScript(
                                                container.fingerprintSeed,
                                                container.blockWebRtc,
                                                activeUaMode
                                            ),
                                            null
                                        )
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                        view?.evaluateJavascript(
                                            sandboxManager.buildAntiDetectShieldScript(
                                                container.fingerprintSeed,
                                                container.blockWebRtc,
                                                activeUaMode
                                            ),
                                            null
                                        )
                                        onPersistCookies()
                                    }
                                }

                                loadUrl(container.url)
                                webViewRef = this
                            }
                        }
                    )
                }
            }

            // Sleek Frosted Bottom Browser Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianMid)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.sandbox_back),
                    tint = if (canGoBack) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                    onClick = { if (canGoBack) webViewRef?.goBack() },
                    modifier = Modifier.testTag("sandbox_back_button")
                )
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.sandbox_forward),
                    tint = if (canGoForward) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                    onClick = { if (canGoForward) webViewRef?.goForward() },
                    modifier = Modifier.testTag("sandbox_forward_button")
                )

                // Compact URL Bar
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50.dp))
                        .background(GlassSurfaceFill)
                        .border(1.dp, GlassBorderMedium, RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = EmeraldGlow,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            color = TextPrimary,
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        cursorBrush = SolidColor(NeonCyanBright),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                val normalized = ContainerStore.normalizeHttpsUrl(urlInput)
                                currentUrl = normalized
                                urlInput = normalized
                                webViewRef?.loadUrl(normalized)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sandbox_url_input")
                    )
                }

                GlassIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.sandbox_reload),
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier.testTag("sandbox_reload_button")
                )

                GlassIconButton(
                    icon = Icons.Rounded.Shield,
                    contentDescription = stringResource(R.string.sandbox_shield_info),
                    tint = EmeraldGlow,
                    accentGlow = EmeraldGlow,
                    onClick = { showShieldDialog = true },
                    modifier = Modifier.testTag("sandbox_shield_button")
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = ObsidianElevated,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }

    if (showShieldDialog) {
        AlertDialog(
            onDismissRequest = { showShieldDialog = false },
            containerColor = ObsidianMid,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Active Stealth Telemetry",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Container ID: ${container.id}", style = MaterialTheme.typography.labelSmall, color = NeonCyanBright)
                    Text("Fingerprint Seed: ${container.fingerprintSeed}", style = MaterialTheme.typography.labelSmall, color = CyberViolet)
                    Text(
                        "WebRTC Leak Blocker: ${if (container.blockWebRtc) "ACTIVE" else "OFF"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldGlow
                    )
                    Text("Spoofed UA:\n$activeUaString", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showShieldDialog = false }) {
                    Text("Done", color = NeonCyanBright)
                }
            }
        )
    }
}
