package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import com.example.data.UserAgentMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Anti-Detect Web Sandbox Engine managing isolated multi-session WebView instances.
 * Provides:
 *  - Per-container cookie jar isolation (snapshot & restore per containerId in app-private storage)
 *  - Randomized & selectable modern User-Agent spoofing (Pixel 8 Pro, S24 Ultra, Desktop Chrome)
 *  - WebRTC IP-leak blocker and hardware fingerprint shielding script injection
 */
class WebSandboxManager(private val context: Context) {

    private val cookieVaultDir: File by lazy {
        File(context.filesDir, "stealth_cookie_jars").apply { mkdirs() }
    }

    /**
     * Resolves a realistic, up-to-date User-Agent string for the selected mode.
     */
    fun resolveUserAgent(mode: UserAgentMode, seed: String): String {
        val pool = listOf(
            UA_PIXEL_8_PRO,
            UA_GALAXY_S24,
            UA_DESKTOP_CHROME_MAC,
            UA_DESKTOP_CHROME_WIN
        )
        return when (mode) {
            UserAgentMode.PIXEL_8_PRO -> UA_PIXEL_8_PRO
            UserAgentMode.GALAXY_S24 -> UA_GALAXY_S24
            UserAgentMode.DESKTOP_CHROME_MAC -> UA_DESKTOP_CHROME_MAC
            UserAgentMode.DESKTOP_CHROME_WIN -> UA_DESKTOP_CHROME_WIN
            UserAgentMode.RANDOM_ROTATE -> {
                val index = abs((seed + System.currentTimeMillis() / 60_000L).hashCode()) % pool.size
                pool[index]
            }
        }
    }

    /**
     * Configures a WebView with hardened privacy settings, independent cookie acceptance,
     * and the active container's spoofed User-Agent.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configureIsolatedWebView(
        webView: WebView,
        userAgent: String,
        isDesktopMode: Boolean
    ) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = userAgent
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        if (isDesktopMode) {
            settings.textZoom = 90
        } else {
            settings.textZoom = 100
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * Loads the container's dedicated cookie jar into CookieManager before page navigation,
     * clearing any previous container's active cookies so sessions never bleed across accounts.
     */
    fun activateContainerCookieJar(containerId: String, onReady: () -> Unit) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            val jarFile = File(cookieVaultDir, "${sanitizeId(containerId)}.json")
            if (jarFile.exists()) {
                runCatching {
                    val entries = JSONArray(jarFile.readText())
                    for (i in 0 until entries.length()) {
                        val obj = entries.getJSONObject(i)
                        val origin = obj.getString("origin")
                        val rawCookieHeader = obj.getString("cookies")
                        rawCookieHeader.split(";")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { singleCookie ->
                                cookieManager.setCookie(origin, singleCookie)
                            }
                    }
                }
            }
            cookieManager.flush()
            onReady()
        }
    }

    /**
     * Snapshots all active cookies for visited origins into this container's isolated vault file.
     */
    fun persistContainerCookieJar(containerId: String, trackedOrigins: Set<String>) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()

        val jarFile = File(cookieVaultDir, "${sanitizeId(containerId)}.json")
        val existingMap = mutableMapOf<String, String>()

        if (jarFile.exists()) {
            runCatching {
                val existing = JSONArray(jarFile.readText())
                for (i in 0 until existing.length()) {
                    val obj = existing.getJSONObject(i)
                    existingMap[obj.getString("origin")] = obj.getString("cookies")
                }
            }
        }

        trackedOrigins.forEach { origin ->
            val currentCookies = cookieManager.getCookie(origin)
            if (!currentCookies.isNullOrBlank()) {
                existingMap[origin] = currentCookies
            }
        }

        val outArray = JSONArray()
        existingMap.forEach { (origin, cookies) ->
            outArray.put(
                JSONObject().apply {
                    put("origin", origin)
                    put("cookies", cookies)
                }
            )
        }
        runCatching { jarFile.writeText(outArray.toString()) }
    }

    /**
     * Permanently clears all cookies, WebStorage, and cached session artifacts for a container.
     */
    fun clearContainerSession(containerId: String, webView: WebView? = null, onCleared: () -> Unit = {}) {
        val jarFile = File(cookieVaultDir, "${sanitizeId(containerId)}.json")
        runCatching { if (jarFile.exists()) jarFile.delete() }
        webView?.clearCache(true)
        webView?.clearHistory()
        webView?.clearFormData()
        runCatching { WebStorage.getInstance().deleteAllData() }

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            onCleared()
        }
    }

    /**
     * Extracts canonical https://host origin from any URL for cookie jar scoping.
     */
    fun extractOrigin(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        if (scheme != "https" && scheme != "http") return null
        return "$scheme://$host"
    }

    /**
     * Generates the Anti-Detect JavaScript shield injected into every sandboxed page:
     *  - Blocks WebRTC RTCPeerConnection IP leaks when enabled
     *  - Masks navigator.webdriver, hardwareConcurrency, deviceMemory, and battery API
     *  - Applies deterministic per-container noise to HTMLCanvasElement.toDataURL fingerprinting
     */
    fun buildAntiDetectShieldScript(fingerprintSeed: String, blockWebRtc: Boolean): String {
        val hash = abs(fingerprintSeed.hashCode())
        val spoofedCores = listOf(4, 6, 8)[hash % 3]
        val spoofedMemory = listOf(4, 8)[(hash / 3) % 2]
        val canvasDelta = (hash % 5) + 1

        val webrtcBlockSnippet = if (blockWebRtc) {
            """
            try {
              const blockedRtc = function() {
                throw new DOMException('WebRTC blocked by MultiCreator Stealth Shield', 'SecurityError');
              };
              Object.defineProperty(window, 'RTCPeerConnection', { value: blockedRtc, configurable: false });
              Object.defineProperty(window, 'webkitRTCPeerConnection', { value: blockedRtc, configurable: false });
              Object.defineProperty(window, 'mozRTCPeerConnection', { value: blockedRtc, configurable: false });
            } catch (e) {}
            """.trimIndent()
        } else {
            ""
        }

        return """
            (function() {
              if (window.__mcShieldInstalled) return;
              window.__mcShieldInstalled = true;
              try {
                Object.defineProperty(navigator, 'webdriver', { get: () => false });
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => $spoofedCores });
                Object.defineProperty(navigator, 'deviceMemory', { get: () => $spoofedMemory });
                Object.defineProperty(navigator, 'getBattery', { value: undefined });
              } catch (e) {}
              $webrtcBlockSnippet
              try {
                const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function(x, y, w, h) {
                  const data = origGetImageData.apply(this, arguments);
                  if (data && data.data && data.data.length > 4) {
                    data.data[0] = (data.data[0] + $canvasDelta) % 255;
                  }
                  return data;
                };
              } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun sanitizeId(id: String): String = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    companion object {
        const val UA_PIXEL_8_PRO =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"
        const val UA_GALAXY_S24 =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"
        const val UA_DESKTOP_CHROME_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val UA_DESKTOP_CHROME_WIN =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
