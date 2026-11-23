package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ContainerStore
import com.example.data.UserAgentMode
import com.example.engine.AppContainer
import com.example.engine.ContainerEngine
import com.example.engine.WebSandboxManager
import com.example.ui.theme.CyberGlassBackground
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassBadge
import com.example.ui.theme.GlassBorderMedium
import com.example.ui.theme.GlassIconButton
import com.example.ui.theme.GlassSurfaceFill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidPillButton
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Dedicated runtime activity launched when opening any cloned instance from MultiCreator
 * or directly from a pinned Home Screen shortcut.
 *
 * - For web-capable social/creator apps (Instagram, YouTube, WhatsApp, X, TikTok, etc.):
 *   Runs a full-screen hardware-accelerated anti-detect WebView with an isolated per-container
 *   CookieManager vault inside files/containers/{containerId}/cookies/, randomized User-Agent,
 *   and WebRTC / fingerprint shielding.
 * - For native utilities: Launches the target app with isolated multi-task flags or allows
 *   attaching any HTTPS web endpoint into the clone's isolated sandbox.
 */
class ContainerHostActivity : ComponentActivity() {

    private lateinit var containerEngine: ContainerEngine
    private lateinit var sandboxManager: WebSandboxManager
    private val visitedOrigins = linkedSetOf<String>()
    private var activeContainerId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        containerEngine = ContainerEngine(applicationContext)
        sandboxManager = WebSandboxManager(applicationContext)

        val containerId = intent.getStringExtra(EXTRA_CONTAINER_ID).orEmpty()
        val packageNameExtra = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val appNameExtra = intent.getStringExtra(EXTRA_APP_NAME).orEmpty()
        activeContainerId = containerId

        setContent {
            MultiCreatorTheme {
                ContainerHostScreen(
                    initialContainerId = containerId,
                    fallbackPackageName = packageNameExtra,
                    fallbackAppName = appNameExtra,
                    containerEngine = containerEngine,
                    sandboxManager = sandboxManager,
                    onTrackOrigin = { origin -> visitedOrigins.add(origin) },
                    onSaveCookies = { cid -> persistCookiesToContainerFile(cid) },
                    onRestoreCookies = { cid, onReady -> restoreCookiesFromContainerFile(cid, onReady) },
                    onLaunchWorkClone = { pkg -> launchRealWorkProfileClone(pkg) },
                    onExit = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (activeContainerId.isNotBlank()) {
            persistCookiesToContainerFile(activeContainerId)
        }
    }

    override fun onDestroy() {
        if (activeContainerId.isNotBlank()) {
            persistCookiesToContainerFile(activeContainerId)
        }
        super.onDestroy()
    }

    private fun restoreCookiesFromContainerFile(containerId: String, onReady: () -> Unit) {
        if (sandboxManager.isMultiProfileSupported()) {
            onReady()
            return
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            val jarFile = containerEngine.getCookieJarFile(containerId)
            if (jarFile.exists()) {
                runCatching {
                    val entries = JSONArray(jarFile.readText())
                    for (i in 0 until entries.length()) {
                        val obj = entries.getJSONObject(i)
                        val origin = obj.getString("origin")
                        val cookies = obj.getString("cookies")
                        cookies.split(";")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { cookieManager.setCookie(origin, it) }
                    }
                }
            }
            cookieManager.flush()
            onReady()
        }
    }

    private fun persistCookiesToContainerFile(containerId: String) {
        if (sandboxManager.isMultiProfileSupported()) {
            sandboxManager.persistContainerCookieJar(containerId, visitedOrigins)
            return
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        val jarFile: File = containerEngine.getCookieJarFile(containerId)
        val merged = mutableMapOf<String, String>()

        if (jarFile.exists()) {
            runCatching {
                val arr = JSONArray(jarFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    merged[obj.getString("origin")] = obj.getString("cookies")
                }
            }
        }

        visitedOrigins.forEach { origin ->
            val raw = cookieManager.getCookie(origin)
            if (!raw.isNullOrBlank()) {
                merged[origin] = raw
            }
        }

        val out = JSONArray()
        merged.forEach { (origin, cookies) ->
            out.put(JSONObject().apply {
                put("origin", origin)
                put("cookies", cookies)
            })
        }
        runCatching { jarFile.writeText(out.toString()) }
    }

    private fun launchRealWorkProfileClone(targetPackage: String): Boolean {
        return com.example.engine.WorkProfileManager(applicationContext)
            .launchAppInWorkProfile(targetPackage)
    }

    companion object {
        const val ACTION_LAUNCH_CONTAINER = "com.example.action.LAUNCH_CONTAINER"
        const val EXTRA_CONTAINER_ID = "EXTRA_CONTAINER_ID"
        const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
        const val EXTRA_APP_NAME = "EXTRA_APP_NAME"

        fun createLaunchIntent(context: Context, container: AppContainer): Intent {
            return Intent(context, ContainerHostActivity::class.java).apply {
                action = ACTION_LAUNCH_CONTAINER
                putExtra(EXTRA_CONTAINER_ID, container.id)
                putExtra(EXTRA_PACKAGE_NAME, container.packageName)
                putExtra(EXTRA_APP_NAME, container.appName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

@Composable
private fun ContainerHostScreen(
    initialContainerId: String,
    fallbackPackageName: String,
    fallbackAppName: String,
    containerEngine: ContainerEngine,
    sandboxManager: WebSandboxManager,
    onTrackOrigin: (String) -> Unit,
    onSaveCookies: (String) -> Unit,
    onRestoreCookies: (String, () -> Unit) -> Unit,
    onLaunchWorkClone: (String) -> Boolean,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var container by remember { mutableStateOf<AppContainer?>(null) }
    var cookieJarReady by remember { mutableStateOf(false) }
    var activeWebUrl by remember { mutableStateOf<String?>(null) }
    var urlBarInput by remember { mutableStateOf("") }
    var customUrlInput by remember { mutableStateOf("https://") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showUaMenu by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(initialContainerId) {
        val loaded = if (initialContainerId.isNotBlank()) {
            containerEngine.getContainerById(initialContainerId)
        } else {
            null
        } ?: AppContainer(
            id = initialContainerId.ifBlank { "clone_ephemeral" },
            packageName = fallbackPackageName,
            appName = fallbackAppName.ifBlank { "Cloned Instance" },
            clonedAt = System.currentTimeMillis(),
            virtualSsaid = ContainerEngine.generateVirtualSsaid(),
            containerPath = "",
            webTargetUrl = ContainerEngine.resolveWebPortalForPackage(fallbackPackageName)
        )

        container = loaded
        val initialUrl = loaded.webTargetUrl
        activeWebUrl = initialUrl
        if (initialUrl != null) {
            urlBarInput = initialUrl
            sandboxManager.extractOrigin(initialUrl)?.let(onTrackOrigin)
            onRestoreCookies(loaded.id) {
                cookieJarReady = true
            }
        } else {
            cookieJarReady = true
        }
    }

    DisposableEffect(container?.id) {
        onDispose {
            container?.id?.let(onSaveCookies)
            webViewRef?.destroy()
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            container?.id?.let(onSaveCookies)
            onExit()
        }
    }

    val active = container

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sleek Frosted Top Header Overlay (Exit Clone, Rotate Identity, Clear Cache)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(ObsidianDeep, ObsidianMid))
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
                        icon = Icons.AutoMirrored.Rounded.ExitToApp,
                        contentDescription = "Exit Clone",
                        tint = TextPrimary,
                        onClick = {
                            active?.id?.let(onSaveCookies)
                            onExit()
                        },
                        modifier = Modifier.testTag("container_exit_button")
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = active?.displayLabel ?: "Cloned Space",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassBadge(
                                label = "SSAID ${active?.virtualSsaid?.take(8) ?: "--------"}",
                                color = EmeraldGlow,
                                pulsingDot = true
                            )
                            if (activeWebUrl != null && active != null) {
                                GlassBadge(
                                    label = if (sandboxManager.isMultiProfileSupported()) {
                                        "Profile • ${active.userAgentMode.badgeLabel}"
                                    } else {
                                        active.userAgentMode.badgeLabel
                                    },
                                    color = NeonCyanBright
                                )
                            }
                        }
                    }

                    // User-Agent Selector (for web sandbox mode)
                    if (activeWebUrl != null && active != null) {
                        Box {
                            GlassIconButton(
                                icon = Icons.Rounded.Devices,
                                contentDescription = "Switch User-Agent",
                                tint = NeonCyanBright,
                                onClick = { showUaMenu = true },
                                modifier = Modifier.testTag("container_ua_button")
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
                                                text = if (mode == active.userAgentMode) "✓ ${mode.displayName}" else mode.displayName,
                                                color = if (mode == active.userAgentMode) NeonCyanBright else TextPrimary
                                            )
                                        },
                                        onClick = {
                                            showUaMenu = false
                                            scope.launch {
                                                val updated = containerEngine.updateContainerConfig(
                                                    containerId = active.id,
                                                    userAgentMode = mode
                                                ) ?: active.copy(userAgentMode = mode)
                                                container = updated
                                                val ua = sandboxManager.resolveUserAgent(mode, updated.virtualSsaid)
                                                val desktop = mode == UserAgentMode.DESKTOP_CHROME_MAC ||
                                                    mode == UserAgentMode.DESKTOP_CHROME_WIN
                                                webViewRef?.let { wv ->
                                                    sandboxManager.configureIsolatedWebView(wv, ua, desktop)
                                                    wv.reload()
                                                }
                                                snackbarHostState.showSnackbar("User-Agent switched to ${mode.displayName}")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Rotate Identity / Virtual SSAID
                    GlassIconButton(
                        icon = Icons.Rounded.Autorenew,
                        contentDescription = "Rotate Identity / Fingerprint",
                        tint = NeonCyanBright,
                        accentGlow = NeonCyan,
                        onClick = {
                            if (active == null) return@GlassIconButton
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                val rotated = containerEngine.rotateContainerIdentity(active.id)
                                    ?: active.copy(virtualSsaid = ContainerEngine.generateVirtualSsaid())
                                container = rotated
                                val ua = sandboxManager.resolveUserAgent(rotated.userAgentMode, rotated.virtualSsaid)
                                val desktop = rotated.userAgentMode == UserAgentMode.DESKTOP_CHROME_MAC ||
                                    rotated.userAgentMode == UserAgentMode.DESKTOP_CHROME_WIN
                                webViewRef?.let { wv ->
                                    sandboxManager.configureIsolatedWebView(wv, ua, desktop)
                                    wv.reload()
                                }
                                snackbarHostState.showSnackbar("Identity rotated • New SSAID ${rotated.virtualSsaid.take(8)}")
                            }
                        },
                        modifier = Modifier.testTag("container_rotate_identity_button")
                    )

                    // Clear Container Cache & Cookies
                    GlassIconButton(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear Container Cache",
                        tint = RoseAlert,
                        onClick = {
                            if (active == null) return@GlassIconButton
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                containerEngine.clearContainerCache(active.id)
                                sandboxManager.clearContainerSession(active.id, webViewRef) {
                                    activeWebUrl?.let { webViewRef?.loadUrl(it) }
                                }
                                snackbarHostState.showSnackbar("Cleared isolated Chromium profile cache & cookie vault.")
                            }
                        },
                        modifier = Modifier.testTag("container_clear_cache_button")
                    )
                }

                if (activeWebUrl != null && loadProgress in 1..99) {
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

            // Main Runtime Surface: Isolated WebView OR Native Container Console
            if (active != null && activeWebUrl != null && cookieJarReady) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                sandboxManager.attachIsolatedProfileAndShield(
                                    webView = this,
                                    containerId = active.id,
                                    mode = active.userAgentMode,
                                    fingerprintSeed = active.virtualSsaid,
                                    blockWebRtc = true
                                )

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val target = request?.url?.toString() ?: return false
                                        return !(target.startsWith("https://") || target.startsWith("http://"))
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (!url.isNullOrBlank()) {
                                            urlBarInput = url
                                            sandboxManager.extractOrigin(url)?.let(onTrackOrigin)
                                        }
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                        val seed = container?.virtualSsaid ?: active.virtualSsaid
                                        val uaMode = container?.userAgentMode ?: active.userAgentMode
                                        view?.evaluateJavascript(
                                            sandboxManager.buildAntiDetectShieldScript(seed, true, uaMode),
                                            null
                                        )
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                        val seed = container?.virtualSsaid ?: active.virtualSsaid
                                        val uaMode = container?.userAgentMode ?: active.userAgentMode
                                        view?.evaluateJavascript(
                                            sandboxManager.buildAntiDetectShieldScript(seed, true, uaMode),
                                            null
                                        )
                                        onSaveCookies(active.id)
                                    }
                                }

                                loadUrl(activeWebUrl!!)
                                webViewRef = this
                            }
                        }
                    )
                }

                // Bottom isolated browser bar
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
                        contentDescription = "Back",
                        tint = if (canGoBack) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                        onClick = { if (canGoBack) webViewRef?.goBack() }
                    )
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                        onClick = { if (canGoForward) webViewRef?.goForward() }
                    )
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
                            value = urlBarInput,
                            onValueChange = { urlBarInput = it },
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
                                    val normalized = ContainerStore.normalizeHttpsUrl(urlBarInput)
                                    urlBarInput = normalized
                                    webViewRef?.loadUrl(normalized)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    GlassIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Reload",
                        onClick = { webViewRef?.reload() }
                    )
                }
            } else if (active != null) {
                // Native Utility Isolated Container Console
                CyberGlassBackground(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            highlightAccent = EmeraldGlow,
                            contentPadding = PaddingValues(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = EmeraldGlow,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Isolated Local Clone Ready",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = "MultiCreator never opens your personal app here. To run a separate native copy of ${active.appName} with a new SSAID & empty data, clone it into Android Work Vault (/data/user/10/) from Home — or attach an isolated HTTPS web portal below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fingerprint,
                                        contentDescription = null,
                                        tint = NeonCyanBright,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Virtual SSAID: ${active.virtualSsaid}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = NeonCyanBright
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderSpecial,
                                        contentDescription = null,
                                        tint = CyberViolet,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = active.containerPath.ifBlank { "/files/containers/${active.id}" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                LiquidPillButton(
                                    text = "Launch ${active.appName} Work Clone (/data/user/10/)",
                                    icon = Icons.Rounded.Launch,
                                    accentColors = listOf(EmeraldGlow, NeonCyan),
                                    onClick = {
                                        val ok = onLaunchWorkClone(active.packageName)
                                        if (!ok) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Not cloned in Work Vault yet. Go back and tap 'Clone Native' to install into /data/user/10/.")
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("container_launch_work_button")
                                )
                            }
                        }

                        // Optional Stealth Web Portal Attachment
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            highlightAccent = NeonCyan,
                            contentPadding = PaddingValues(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Or Open Web Account in This Clone",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Attach any HTTPS login URL to run inside this clone's isolated cookie vault and spoofed fingerprint.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                OutlinedTextField(
                                    value = customUrlInput,
                                    onValueChange = { customUrlInput = it },
                                    label = { Text("HTTPS Web Portal URL") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Language,
                                            contentDescription = null,
                                            tint = NeonCyanBright
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyanBright,
                                        unfocusedBorderColor = GlassBorderMedium,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedLabelColor = NeonCyanBright,
                                        unfocusedLabelColor = TextSecondary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                LiquidPillButton(
                                    text = "Launch Isolated Web Sandbox",
                                    icon = Icons.Rounded.Language,
                                    onClick = {
                                        val normalized = ContainerStore.normalizeHttpsUrl(customUrlInput)
                                        scope.launch {
                                            val updated = containerEngine.updateContainerConfig(
                                                containerId = active.id,
                                                webTargetUrl = normalized
                                            ) ?: active.copy(webTargetUrl = normalized)
                                            container = updated
                                            urlBarInput = normalized
                                            sandboxManager.extractOrigin(normalized)?.let(onTrackOrigin)
                                            onRestoreCookies(updated.id) {
                                                activeWebUrl = normalized
                                                cookieJarReady = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 70.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = ObsidianElevated,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
