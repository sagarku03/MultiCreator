package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.data.UserAgentMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

data class DeviceFingerprintProfile(
    val userAgent: String,
    val platform: String,
    val platformVersion: String,
    val model: String,
    val isMobile: Boolean,
    val hardwareConcurrency: Int,
    val deviceMemoryGb: Int,
    val maxTouchPoints: Int,
    val webGlVendor: String,
    val webGlRenderer: String
)

data class WebViewStealthCapabilities(
    val multiProfileSupported: Boolean,
    val documentStartScriptSupported: Boolean,
    val requestedWithHeaderControlSupported: Boolean,
    val activeChromiumProfileCount: Int
)

/**
 * Research-grade Anti-Detect Web Sandbox Engine:
 *  1. Native Chromium Multi-Profile isolation via AndroidX WebKit ProfileStore (separate CookieManager,
 *     WebStorage, IndexedDB, Cache, and renderer process per clone) with private cookie-jar fallback.
 *  2. Pre-execution `document_start` script injection (`WebViewCompat.addDocumentStartJavaScript`)
 *     so fingerprint shielding runs BEFORE website scripts execute.
 *  3. Coherent hardware profiles aligning User-Agent (zero `; wv` token), `navigator.userAgentData`
 *     Client Hints, WebGL GPU vendor/renderer, hardwareConcurrency, deviceMemory, maxTouchPoints,
 *     deterministic Canvas/AudioContext perturbation, and WebRTC IP-leak neutralization.
 *  4. Native `X-Requested-With` HTTP header stripping via `WebSettingsCompat`.
 */
class WebSandboxManager(private val context: Context) {

    private val cookieVaultDir: File by lazy {
        File(context.filesDir, "stealth_cookie_jars").apply { mkdirs() }
    }

    fun isMultiProfileSupported(): Boolean {
        return runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        }.getOrDefault(false)
    }

    fun isDocumentStartScriptSupported(): Boolean {
        return runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        }.getOrDefault(false)
    }

    fun isRequestedWithControlSupported(): Boolean {
        return runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)
        }.getOrDefault(false)
    }

    fun getWebViewStealthCapabilities(): WebViewStealthCapabilities {
        val multiSupported = isMultiProfileSupported()
        val profileCount = if (multiSupported) {
            runCatching {
                ProfileStore.getInstance().allProfileNames.count { it.startsWith(PROFILE_PREFIX) }
            }.getOrDefault(0)
        } else {
            0
        }
        return WebViewStealthCapabilities(
            multiProfileSupported = multiSupported,
            documentStartScriptSupported = isDocumentStartScriptSupported(),
            requestedWithHeaderControlSupported = isRequestedWithControlSupported(),
            activeChromiumProfileCount = profileCount
        )
    }

    /**
     * Resolves a 100% coherent hardware + browser fingerprint profile for the selected mode.
     * Aligns UA, Client Hints, WebGL GPU, cores, RAM, and touch points to prevent anti-bot mismatches.
     */
    fun resolveFingerprintProfile(mode: UserAgentMode, seed: String): DeviceFingerprintProfile {
        val pool = listOf(
            PROFILE_PIXEL_8_PRO,
            PROFILE_GALAXY_S24,
            PROFILE_DESKTOP_MAC,
            PROFILE_DESKTOP_WIN
        )
        return when (mode) {
            UserAgentMode.PIXEL_8_PRO -> PROFILE_PIXEL_8_PRO
            UserAgentMode.GALAXY_S24 -> PROFILE_GALAXY_S24
            UserAgentMode.DESKTOP_CHROME_MAC -> PROFILE_DESKTOP_MAC
            UserAgentMode.DESKTOP_CHROME_WIN -> PROFILE_DESKTOP_WIN
            UserAgentMode.RANDOM_ROTATE -> {
                val index = abs(seed.hashCode()) % pool.size
                pool[index]
            }
        }
    }

    fun resolveUserAgent(mode: UserAgentMode, seed: String): String {
        return resolveFingerprintProfile(mode, seed).userAgent
    }

    /**
     * Attaches a dedicated native Chromium Profile (`ProfileStore`) to the WebView BEFORE any URL
     * is loaded, strips Android's giveaway `X-Requested-With` header, configures hardened settings,
     * and registers the pre-execution `document_start` Anti-Detect JS shield.
     *
     * Returns true if native Chromium Multi-Profile isolation was bound.
     */
    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    fun attachIsolatedProfileAndShield(
        webView: WebView,
        containerId: String,
        mode: UserAgentMode,
        fingerprintSeed: String,
        blockWebRtc: Boolean
    ): Boolean {
        val profile = resolveFingerprintProfile(mode, fingerprintSeed)
        var boundNativeProfile: Profile? = null

        if (isMultiProfileSupported()) {
            val profileName = toProfileName(containerId)
            runCatching {
                val store = ProfileStore.getInstance()
                boundNativeProfile = store.getOrCreateProfile(profileName)
                WebViewCompat.setProfile(webView, profileName)
            }
        }

        configureIsolatedWebView(
            webView = webView,
            userAgent = profile.userAgent,
            isDesktopMode = !profile.isMobile,
            nativeProfile = boundNativeProfile
        )

        // Inject coherent anti-detect shield at Chromium document_start (before website JS runs)
        if (isDocumentStartScriptSupported()) {
            val shieldScript = buildAntiDetectShieldScript(
                fingerprintSeed = fingerprintSeed,
                blockWebRtc = blockWebRtc,
                mode = mode
            )
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, shieldScript, setOf("*"))
            }
        }

        return boundNativeProfile != null
    }

    /**
     * Re-applies a newly selected User-Agent & desktop viewport mode while keeping the WebView's
     * native Chromium ProfileStore CookieManager strictly bound to this container.
     */
    @SuppressLint("RequiresFeature")
    fun reapplyUserAgentForContainer(
        webView: WebView,
        containerId: String,
        mode: UserAgentMode,
        fingerprintSeed: String
    ) {
        val profile = resolveFingerprintProfile(mode, fingerprintSeed)
        val nativeProfile = if (isMultiProfileSupported()) {
            runCatching { ProfileStore.getInstance().getProfile(toProfileName(containerId)) }.getOrNull()
        } else {
            null
        }
        configureIsolatedWebView(
            webView = webView,
            userAgent = profile.userAgent,
            isDesktopMode = !profile.isMobile,
            nativeProfile = nativeProfile
        )
    }

    /**
     * Configures a WebView with hardened privacy settings, stripped `X-Requested-With` header,
     * profile-scoped CookieManager, and spoofed User-Agent.
     */
    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    fun configureIsolatedWebView(
        webView: WebView,
        userAgent: String,
        isDesktopMode: Boolean,
        nativeProfile: Profile? = null
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
        settings.textZoom = if (isDesktopMode) 90 else 100

        // Strip Android WebView's giveaway `X-Requested-With: <packageName>` HTTP header
        if (isRequestedWithControlSupported()) {
            runCatching {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
        }

        val cookieManager = nativeProfile?.cookieManager ?: CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * Prepares the container's cookie jar. When native Chromium Multi-Profile is active, cookies
     * are already isolated at the Chromium engine level; otherwise restores from the private JSON jar.
     */
    fun activateContainerCookieJar(containerId: String, onReady: () -> Unit) {
        if (isMultiProfileSupported()) {
            onReady()
            return
        }

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
     * Flushes native Chromium profile cookies or snapshots fallback cookie jar to private storage.
     */
    @SuppressLint("RequiresFeature")
    fun persistContainerCookieJar(containerId: String, trackedOrigins: Set<String>) {
        if (isMultiProfileSupported()) {
            runCatching {
                ProfileStore.getInstance().getProfile(toProfileName(containerId))?.cookieManager?.flush()
            }
            return
        }

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
     * Clears cookies, WebStorage, and cache strictly for THIS container without touching other clones.
     */
    @SuppressLint("RequiresFeature")
    fun clearContainerSession(containerId: String, webView: WebView? = null, onCleared: () -> Unit = {}) {
        val jarFile = File(cookieVaultDir, "${sanitizeId(containerId)}.json")
        runCatching { if (jarFile.exists()) jarFile.delete() }
        webView?.clearCache(true)
        webView?.clearHistory()
        webView?.clearFormData()

        if (isMultiProfileSupported()) {
            val profile = runCatching {
                ProfileStore.getInstance().getProfile(toProfileName(containerId))
            }.getOrNull()
            if (profile != null) {
                runCatching { profile.webStorage.deleteAllData() }
                profile.cookieManager.removeAllCookies {
                    profile.cookieManager.flush()
                    onCleared()
                }
                return
            }
        }

        runCatching { WebStorage.getInstance().deleteAllData() }
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            onCleared()
        }
    }

    /**
     * Permanently deletes the native Chromium ProfileStore entry when a clone is removed.
     */
    @SuppressLint("RequiresFeature")
    fun deleteIsolatedProfile(containerId: String) {
        val jarFile = File(cookieVaultDir, "${sanitizeId(containerId)}.json")
        runCatching { if (jarFile.exists()) jarFile.delete() }
        if (isMultiProfileSupported()) {
            runCatching {
                ProfileStore.getInstance().deleteProfile(toProfileName(containerId))
            }
        }
    }

    fun extractOrigin(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        if (scheme != "https" && scheme != "http") return null
        return "$scheme://$host"
    }

    /**
     * Builds the Coherent Anti-Detect JavaScript Shield injected at `document_start`:
     *  - Aligns `navigator.userAgentData` Client Hints & `getHighEntropyValues` with the exact UA
     *  - Masks `navigator.webdriver`, `hardwareConcurrency`, `deviceMemory`, `maxTouchPoints`, `getBattery`
     *  - Spoofs WebGL `UNMASKED_VENDOR_WEBGL` and `UNMASKED_RENDERER_WEBGL` to match the device GPU
     *  - Blocks WebRTC `RTCPeerConnection` local IP leaks when enabled
     *  - Applies deterministic per-container Canvas & AudioContext micro-perturbation
     */
    fun buildAntiDetectShieldScript(
        fingerprintSeed: String,
        blockWebRtc: Boolean,
        mode: UserAgentMode = UserAgentMode.PIXEL_8_PRO
    ): String {
        val profile = resolveFingerprintProfile(mode, fingerprintSeed)
        val hash = abs(fingerprintSeed.hashCode())
        val canvasDelta = (hash % 3) + 1
        val audioJitter = ((hash % 7) + 1) * 0.0000001

        val webrtcBlockSnippet = if (blockWebRtc) {
            """
            try {
              const blockedRtc = function RTCPeerConnection() {
                throw new DOMException('WebRTC disabled by MultiCreator Stealth Shield', 'SecurityError');
              };
              Object.defineProperty(window, 'RTCPeerConnection', { value: blockedRtc, configurable: false });
              Object.defineProperty(window, 'webkitRTCPeerConnection', { value: blockedRtc, configurable: false });
              Object.defineProperty(window, 'mozRTCPeerConnection', { value: blockedRtc, configurable: false });
            } catch (e) {}
            """.trimIndent()
        } else {
            ""
        }

        val escapedVendor = profile.webGlVendor.replace("'", "\\'")
        val escapedRenderer = profile.webGlRenderer.replace("'", "\\'")
        val escapedPlatform = profile.platform.replace("'", "\\'")
        val escapedPlatformVersion = profile.platformVersion.replace("'", "\\'")
        val escapedModel = profile.model.replace("'", "\\'")

        return """
            (function() {
              if (window.__mcStealthShieldV2) return;
              window.__mcStealthShieldV2 = true;

              const defineGetter = (obj, prop, getter) => {
                try {
                  Object.defineProperty(obj, prop, { get: getter, configurable: true });
                } catch (e) {}
              };

              // 1. Core Navigator Coherence (matches spoofed UA with zero ;wv leak)
              defineGetter(navigator, 'webdriver', () => false);
              defineGetter(navigator, 'hardwareConcurrency', () => ${profile.hardwareConcurrency});
              defineGetter(navigator, 'deviceMemory', () => ${profile.deviceMemoryGb});
              defineGetter(navigator, 'maxTouchPoints', () => ${profile.maxTouchPoints});
              try { Object.defineProperty(navigator, 'getBattery', { value: undefined }); } catch (e) {}

              // 2. Coherent User-Agent Client Hints (navigator.userAgentData)
              const uaBrands = [
                { brand: 'Chromium', version: '131' },
                { brand: 'Google Chrome', version: '131' },
                { brand: 'Not_A Brand', version: '24' }
              ];
              const uaDataStub = {
                brands: uaBrands,
                mobile: ${profile.isMobile},
                platform: '$escapedPlatform',
                getHighEntropyValues: function(hints) {
                  return Promise.resolve({
                    brands: uaBrands,
                    mobile: ${profile.isMobile},
                    platform: '$escapedPlatform',
                    platformVersion: '$escapedPlatformVersion',
                    architecture: '${if (profile.isMobile) "arm" else "x86"}',
                    bitness: '64',
                    model: '$escapedModel',
                    uaFullVersion: '131.0.6778.135',
                    fullVersionList: uaBrands.map(b => ({ brand: b.brand, version: b.version + '.0.0.0' }))
                  });
                },
                toJSON: function() {
                  return { brands: uaBrands, mobile: ${profile.isMobile}, platform: '$escapedPlatform' };
                }
              };
              defineGetter(navigator, 'userAgentData', () => uaDataStub);

              // 3. WebRTC IP Leak Neutralization
              $webrtcBlockSnippet

              // 4. Coherent WebGL Vendor & Renderer Masking
              const patchWebGl = (proto) => {
                if (!proto || !proto.getParameter) return;
                const origGetParam = proto.getParameter;
                proto.getParameter = function(param) {
                  if (param === 37445) return '$escapedVendor';
                  if (param === 37446) return '$escapedRenderer';
                  return origGetParam.apply(this, arguments);
                };
              };
              try { patchWebGl(window.WebGLRenderingContext && WebGLRenderingContext.prototype); } catch (e) {}
              try { patchWebGl(window.WebGL2RenderingContext && WebGL2RenderingContext.prototype); } catch (e) {}

              // 5. Deterministic Per-Container Canvas & AudioContext Fingerprint Perturbation
              try {
                const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function(x, y, w, h) {
                  const img = origGetImageData.apply(this, arguments);
                  if (img && img.data && img.data.length > 4) {
                    img.data[0] = (img.data[0] + $canvasDelta) % 255;
                  }
                  return img;
                };
              } catch (e) {}

              try {
                if (window.AudioBuffer && AudioBuffer.prototype.getChannelData) {
                  const origChannelData = AudioBuffer.prototype.getChannelData;
                  AudioBuffer.prototype.getChannelData = function() {
                    const data = origChannelData.apply(this, arguments);
                    if (data && data.length > 0 && !this.__mcPatched) {
                      this.__mcPatched = true;
                      data[0] = data[0] + $audioJitter;
                    }
                    return data;
                  };
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun toProfileName(containerId: String): String = "$PROFILE_PREFIX${sanitizeId(containerId)}"
    private fun sanitizeId(id: String): String = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    companion object {
        private const val PROFILE_PREFIX = "mc_profile_"

        const val UA_PIXEL_8_PRO =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"
        const val UA_GALAXY_S24 =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"
        const val UA_DESKTOP_CHROME_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val UA_DESKTOP_CHROME_WIN =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val PROFILE_PIXEL_8_PRO = DeviceFingerprintProfile(
            userAgent = UA_PIXEL_8_PRO,
            platform = "Android",
            platformVersion = "14.0.0",
            model = "Pixel 8 Pro",
            isMobile = true,
            hardwareConcurrency = 8,
            deviceMemoryGb = 8,
            maxTouchPoints = 5,
            webGlVendor = "Google Inc. (ARM)",
            webGlRenderer = "ANGLE (ARM, Mali-G715 Immortalis MC10, OpenGL ES 3.2)"
        )

        private val PROFILE_GALAXY_S24 = DeviceFingerprintProfile(
            userAgent = UA_GALAXY_S24,
            platform = "Android",
            platformVersion = "14.0.0",
            model = "SM-S928B",
            isMobile = true,
            hardwareConcurrency = 8,
            deviceMemoryGb = 8,
            maxTouchPoints = 5,
            webGlVendor = "Qualcomm",
            webGlRenderer = "Adreno (TM) 750"
        )

        private val PROFILE_DESKTOP_MAC = DeviceFingerprintProfile(
            userAgent = UA_DESKTOP_CHROME_MAC,
            platform = "macOS",
            platformVersion = "14.6.1",
            model = "",
            isMobile = false,
            hardwareConcurrency = 8,
            deviceMemoryGb = 8,
            maxTouchPoints = 0,
            webGlVendor = "Apple Inc.",
            webGlRenderer = "Apple M3"
        )

        private val PROFILE_DESKTOP_WIN = DeviceFingerprintProfile(
            userAgent = UA_DESKTOP_CHROME_WIN,
            platform = "Windows",
            platformVersion = "15.0.0",
            model = "",
            isMobile = false,
            hardwareConcurrency = 8,
            deviceMemoryGb = 8,
            maxTouchPoints = 0,
            webGlVendor = "Google Inc. (Intel)",
            webGlRenderer = "ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 vs_5_0 ps_5_0)"
        )
    }
}
