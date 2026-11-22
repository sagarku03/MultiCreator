package com.example.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.UserAgentMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID

private val Context.localContainerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "multicreator_local_clones_v1"
)

data class AppContainer(
    val id: String,
    val packageName: String,
    val appName: String,
    val slotNumber: Int = 1,
    val clonedAt: Long,
    val virtualSsaid: String,
    val containerPath: String,
    val userAgentMode: UserAgentMode = UserAgentMode.PIXEL_8_PRO,
    val webTargetUrl: String? = null
) {
    val displayLabel: String
        get() = if (slotNumber > 1) "$appName #$slotNumber" else "$appName #1"
}

/**
 * Instant 1-Click Local Container Engine.
 * Creates isolated per-clone filesystem partitions under:
 *   /data/data/<package>/files/containers/{containerId}/
 * with dedicated SharedPreferences, native Chromium ProfileStore isolation, isolated cookie jars,
 * and cryptographically random virtual SSAIDs to break device-ban loops without root or factory resets.
 */
class ContainerEngine(private val context: Context) {

    private val clonesKey = stringPreferencesKey("active_local_clones_json")
    private val containersRootDir: File by lazy {
        File(context.filesDir, "containers").apply { mkdirs() }
    }

    val containersFlow: Flow<List<AppContainer>> =
        context.localContainerDataStore.data.map { prefs ->
            val raw = prefs[clonesKey]
            if (raw.isNullOrBlank()) emptyList() else parseContainers(raw)
        }

    /**
     * Instantly provisions an isolated local container directory, dedicated SharedPreferences,
     * cookie vault folder, and unique 16-character virtual SSAID for the specified app.
     * Supports unlimited multi-slot clones per app (e.g. Instagram #1, #2, #3).
     */
    suspend fun createLocalClone(
        packageName: String,
        appName: String
    ): AppContainer = withContext(Dispatchers.IO) {
        val existingForPkg = getClonesForPackage(packageName)
        val nextSlot = (existingForPkg.maxOfOrNull { it.slotNumber } ?: 0) + 1

        val containerId = "clone_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val virtualSsaid = generateVirtualSsaid()
        val now = System.currentTimeMillis()
        val containerDir = File(containersRootDir, sanitizeId(containerId)).apply {
            mkdirs()
            File(this, "prefs").mkdirs()
            File(this, "cookies").mkdirs()
            File(this, "cache").mkdirs()
        }

        val resolvedWebUrl = resolveWebPortalForPackage(packageName)
        val defaultUa = when {
            packageName.contains("studio") || packageName.contains("whatsapp") -> UserAgentMode.DESKTOP_CHROME_MAC
            nextSlot % 2 == 0 -> UserAgentMode.GALAXY_S24
            else -> UserAgentMode.PIXEL_8_PRO
        }

        val container = AppContainer(
            id = containerId,
            packageName = packageName,
            appName = appName,
            slotNumber = nextSlot,
            clonedAt = now,
            virtualSsaid = virtualSsaid,
            containerPath = containerDir.absolutePath,
            userAgentMode = defaultUa,
            webTargetUrl = resolvedWebUrl
        )

        // Initialize isolated per-container SharedPreferences and on-disk metadata
        getContainerPreferences(containerId).edit()
            .putString("container_id", containerId)
            .putString("package_name", packageName)
            .putString("app_name", appName)
            .putInt("slot_number", nextSlot)
            .putString("virtual_ssaid", virtualSsaid)
            .putLong("created_at", now)
            .apply()

        writeMetadataFile(containerDir, container)

        context.localContainerDataStore.edit { prefs ->
            val existing = prefs[clonesKey]?.let(::parseContainers).orEmpty()
            prefs[clonesKey] = serializeContainers(listOf(container) + existing)
        }

        container
    }

    /**
     * Rotates the container's virtual SSAID and optionally cycles its User-Agent mode,
     * refreshing the isolated identity fingerprint.
     */
    suspend fun rotateContainerIdentity(
        containerId: String,
        newUserAgentMode: UserAgentMode? = null
    ): AppContainer? = withContext(Dispatchers.IO) {
        var updatedContainer: AppContainer? = null
        val freshSsaid = generateVirtualSsaid()

        context.localContainerDataStore.edit { prefs ->
            val existing = prefs[clonesKey]?.let(::parseContainers).orEmpty()
            val mapped = existing.map { item ->
                if (item.id == containerId) {
                    val next = item.copy(
                        virtualSsaid = freshSsaid,
                        userAgentMode = newUserAgentMode ?: nextUserAgentMode(item.userAgentMode)
                    )
                    updatedContainer = next
                    next
                } else {
                    item
                }
            }
            prefs[clonesKey] = serializeContainers(mapped)
        }

        updatedContainer?.let { updated ->
            getContainerPreferences(containerId).edit()
                .putString("virtual_ssaid", updated.virtualSsaid)
                .putLong("last_rotated_at", System.currentTimeMillis())
                .apply()
            val dir = File(updated.containerPath)
            if (dir.exists()) {
                writeMetadataFile(dir, updated)
            }
        }

        updatedContainer
    }

    /**
     * Updates the user-agent mode or custom web URL for an existing container.
     */
    suspend fun updateContainerConfig(
        containerId: String,
        userAgentMode: UserAgentMode? = null,
        webTargetUrl: String? = null
    ): AppContainer? = withContext(Dispatchers.IO) {
        var updatedContainer: AppContainer? = null
        context.localContainerDataStore.edit { prefs ->
            val existing = prefs[clonesKey]?.let(::parseContainers).orEmpty()
            val mapped = existing.map { item ->
                if (item.id == containerId) {
                    val next = item.copy(
                        userAgentMode = userAgentMode ?: item.userAgentMode,
                        webTargetUrl = webTargetUrl ?: item.webTargetUrl
                    )
                    updatedContainer = next
                    next
                } else {
                    item
                }
            }
            prefs[clonesKey] = serializeContainers(mapped)
        }
        updatedContainer
    }

    /**
     * Clears only cache and cookie files inside the isolated container without deleting the clone.
     */
    suspend fun clearContainerCache(containerId: String) = withContext(Dispatchers.IO) {
        val dir = File(containersRootDir, sanitizeId(containerId))
        File(dir, "cache").deleteRecursively()
        File(dir, "cookies").deleteRecursively()
        File(dir, "cache").mkdirs()
        File(dir, "cookies").mkdirs()
    }

    /**
     * Permanently deletes the isolated container directory, SharedPreferences, and DataStore record.
     */
    suspend fun deleteClone(containerId: String) = withContext(Dispatchers.IO) {
        val dir = File(containersRootDir, sanitizeId(containerId))
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        context.deleteSharedPreferences("container_prefs_${sanitizeId(containerId)}")
        WebSandboxManager(context).deleteIsolatedProfile(containerId)

        context.localContainerDataStore.edit { prefs ->
            val existing = prefs[clonesKey]?.let(::parseContainers).orEmpty()
            prefs[clonesKey] = serializeContainers(existing.filterNot { it.id == containerId })
        }
    }

    /**
     * Returns all active clones registered for the given package name.
     */
    suspend fun getClonesForPackage(packageName: String): List<AppContainer> {
        return containersFlow.first().filter { it.packageName == packageName }
    }

    /**
     * Resolves a single container by its unique ID.
     */
    suspend fun getContainerById(containerId: String): AppContainer? {
        return containersFlow.first().firstOrNull { it.id == containerId }
    }

    /**
     * Returns the isolated SharedPreferences instance dedicated to a specific container.
     */
    fun getContainerPreferences(containerId: String): SharedPreferences {
        return context.getSharedPreferences(
            "container_prefs_${sanitizeId(containerId)}",
            Context.MODE_PRIVATE
        )
    }

    /**
     * Returns the private cookie jar JSON file inside this container's isolated folder.
     */
    fun getCookieJarFile(containerId: String): File {
        val cookiesDir = File(File(containersRootDir, sanitizeId(containerId)), "cookies").apply {
            mkdirs()
        }
        return File(cookiesDir, "cookie_jar.json")
    }

    private fun writeMetadataFile(dir: File, container: AppContainer) {
        runCatching {
            val meta = JSONObject().apply {
                put("id", container.id)
                put("packageName", container.packageName)
                put("appName", container.appName)
                put("clonedAt", container.clonedAt)
                put("virtualSsaid", container.virtualSsaid)
                put("userAgentMode", container.userAgentMode.name)
                put("webTargetUrl", container.webTargetUrl ?: JSONObject.NULL)
            }
            File(dir, "metadata.json").writeText(meta.toString(2))
        }
    }

    private fun serializeContainers(list: List<AppContainer>): String {
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("packageName", item.packageName)
                    put("appName", item.appName)
                    put("slotNumber", item.slotNumber)
                    put("clonedAt", item.clonedAt)
                    put("virtualSsaid", item.virtualSsaid)
                    put("containerPath", item.containerPath)
                    put("userAgentMode", item.userAgentMode.name)
                    if (item.webTargetUrl != null) {
                        put("webTargetUrl", item.webTargetUrl)
                    }
                }
            )
        }
        return array.toString()
    }

    private fun parseContainers(json: String): List<AppContainer> {
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val path = obj.optString(
                        "containerPath",
                        File(containersRootDir, sanitizeId(id)).absolutePath
                    )
                    val webUrl = obj.optString("webTargetUrl", "").ifBlank { null }
                    add(
                        AppContainer(
                            id = id,
                            packageName = obj.getString("packageName"),
                            appName = obj.getString("appName"),
                            slotNumber = obj.optInt("slotNumber", 1),
                            clonedAt = obj.optLong("clonedAt", System.currentTimeMillis()),
                            virtualSsaid = obj.optString("virtualSsaid", generateVirtualSsaid()),
                            containerPath = path,
                            userAgentMode = UserAgentMode.fromNameOrDefault(
                                obj.optString("userAgentMode")
                            ),
                            webTargetUrl = webUrl
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun nextUserAgentMode(current: UserAgentMode): UserAgentMode {
        val list = listOf(
            UserAgentMode.PIXEL_8_PRO,
            UserAgentMode.GALAXY_S24,
            UserAgentMode.DESKTOP_CHROME_MAC,
            UserAgentMode.DESKTOP_CHROME_WIN
        )
        val idx = list.indexOf(current)
        return list[(idx + 1) % list.size]
    }

    private fun sanitizeId(id: String): String = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    companion object {
        private val secureRandom = SecureRandom()

        fun generateVirtualSsaid(): String {
            val bytes = ByteArray(8)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02X".format(it) }
        }

        /**
         * Maps major social, creator, messaging, and media apps to their official HTTPS web
         * endpoints so cloned containers launch immediately inside a zero-hardware-footprint
         * anti-detect isolated WebView session.
         */
        fun resolveWebPortalForPackage(packageName: String): String? {
            val pkg = packageName.lowercase()
            return when {
                pkg.contains("instagram") -> "https://www.instagram.com/"
                pkg.contains("youtube.creator") -> "https://studio.youtube.com/"
                pkg.contains("youtube") -> "https://m.youtube.com/"
                pkg.contains("whatsapp") -> "https://web.whatsapp.com/"
                pkg.contains("twitter") || pkg == "com.x.android" -> "https://x.com/home"
                pkg.contains("musically") || pkg.contains("tiktok") -> "https://www.tiktok.com/"
                pkg.contains("facebook.katana") || pkg.contains("facebook.lite") -> "https://m.facebook.com/"
                pkg.contains("barcelona") || pkg.contains("threads") -> "https://www.threads.net/"
                pkg.contains("reddit") -> "https://www.reddit.com/"
                pkg.contains("snapchat") -> "https://web.snapchat.com/"
                pkg.contains("telegram") -> "https://web.telegram.org/"
                pkg.contains("discord") -> "https://discord.com/app"
                pkg.contains("linkedin") -> "https://www.linkedin.com/"
                pkg.contains("pinterest") -> "https://www.pinterest.com/"
                pkg.contains("twitch") -> "https://m.twitch.tv/"
                pkg.contains("spotify") -> "https://open.spotify.com/"
                pkg.contains("google.android.gm") -> "https://mail.google.com/"
                pkg.contains("chrome") || pkg.contains("browser") -> "https://duckduckgo.com/"
                else -> null
            }
        }
    }
}
