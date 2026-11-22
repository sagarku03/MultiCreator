package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.multiCreatorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "multicreator_containers_v1"
)

enum class UserAgentMode(val displayName: String, val badgeLabel: String) {
    PIXEL_8_PRO("Pixel 8 Pro (Android 14)", "Pixel 8 Pro"),
    GALAXY_S24("Galaxy S24 Ultra", "S24 Ultra"),
    DESKTOP_CHROME_MAC("Desktop Chrome (macOS)", "macOS Chrome"),
    DESKTOP_CHROME_WIN("Desktop Chrome (Windows)", "Win11 Chrome"),
    RANDOM_ROTATE("Randomized Per Session", "Auto-Rotate");

    companion object {
        fun fromNameOrDefault(name: String?): UserAgentMode {
            return entries.firstOrNull { it.name == name } ?: PIXEL_8_PRO
        }
    }
}

data class WebContainerProfile(
    val id: String,
    val name: String,
    val url: String,
    val platformTag: String,
    val userAgentMode: UserAgentMode,
    val fingerprintSeed: String,
    val blockWebRtc: Boolean = true,
    val accentHex: Long = 0xFF06B6D4L,
    val lastOpenedEpochMs: Long = 0L
)

/**
 * Local DataStore persistence for user-managed isolated Web Stealth containers.
 */
class ContainerStore(private val context: Context) {

    private val containersKey = stringPreferencesKey("web_containers_json")

    val containersFlow: Flow<List<WebContainerProfile>> =
        context.multiCreatorDataStore.data.map { prefs ->
            val raw = prefs[containersKey]
            if (raw.isNullOrBlank()) {
                defaultPresetContainers()
            } else {
                parseContainers(raw)
            }
        }

    suspend fun saveContainers(containers: List<WebContainerProfile>) {
        context.multiCreatorDataStore.edit { prefs ->
            prefs[containersKey] = serializeContainers(containers)
        }
    }

    suspend fun addContainer(
        name: String,
        url: String,
        platformTag: String,
        userAgentMode: UserAgentMode,
        blockWebRtc: Boolean,
        accentHex: Long
    ): WebContainerProfile {
        val normalizedUrl = normalizeHttpsUrl(url)
        val newContainer = WebContainerProfile(
            id = "stealth_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            url = normalizedUrl,
            platformTag = platformTag.trim().ifBlank { "Custom" },
            userAgentMode = userAgentMode,
            fingerprintSeed = generateFingerprintSeed(),
            blockWebRtc = blockWebRtc,
            accentHex = accentHex,
            lastOpenedEpochMs = System.currentTimeMillis()
        )
        context.multiCreatorDataStore.edit { prefs ->
            val existing = prefs[containersKey]?.let(::parseContainers) ?: defaultPresetContainers()
            prefs[containersKey] = serializeContainers(listOf(newContainer) + existing)
        }
        return newContainer
    }

    suspend fun deleteContainer(containerId: String) {
        context.multiCreatorDataStore.edit { prefs ->
            val existing = prefs[containersKey]?.let(::parseContainers) ?: defaultPresetContainers()
            prefs[containersKey] = serializeContainers(existing.filterNot { it.id == containerId })
        }
    }

    suspend fun rotateFingerprint(containerId: String): String {
        val newSeed = generateFingerprintSeed()
        context.multiCreatorDataStore.edit { prefs ->
            val existing = prefs[containersKey]?.let(::parseContainers) ?: defaultPresetContainers()
            val updated = existing.map { item ->
                if (item.id == containerId) item.copy(fingerprintSeed = newSeed) else item
            }
            prefs[containersKey] = serializeContainers(updated)
        }
        return newSeed
    }

    suspend fun markContainerOpened(containerId: String) {
        val now = System.currentTimeMillis()
        context.multiCreatorDataStore.edit { prefs ->
            val existing = prefs[containersKey]?.let(::parseContainers) ?: defaultPresetContainers()
            val updated = existing.map { item ->
                if (item.id == containerId) item.copy(lastOpenedEpochMs = now) else item
            }
            prefs[containersKey] = serializeContainers(updated)
        }
    }

    private fun serializeContainers(list: List<WebContainerProfile>): String {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("url", item.url)
                put("platformTag", item.platformTag)
                put("userAgentMode", item.userAgentMode.name)
                put("fingerprintSeed", item.fingerprintSeed)
                put("blockWebRtc", item.blockWebRtc)
                put("accentHex", item.accentHex)
                put("lastOpenedEpochMs", item.lastOpenedEpochMs)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseContainers(json: String): List<WebContainerProfile> {
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    add(
                        WebContainerProfile(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            url = obj.getString("url"),
                            platformTag = obj.optString("platformTag", "Web"),
                            userAgentMode = UserAgentMode.fromNameOrDefault(
                                obj.optString("userAgentMode")
                            ),
                            fingerprintSeed = obj.optString(
                                "fingerprintSeed",
                                generateFingerprintSeed()
                            ),
                            blockWebRtc = obj.optBoolean("blockWebRtc", true),
                            accentHex = obj.optLong("accentHex", 0xFF06B6D4L),
                            lastOpenedEpochMs = obj.optLong("lastOpenedEpochMs", 0L)
                        )
                    )
                }
            }
        }.getOrElse { defaultPresetContainers() }
    }

    companion object {
        fun generateFingerprintSeed(): String {
            return UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        }

        fun normalizeHttpsUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return "https://www.instagram.com/"
            return when {
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.startsWith("http://", ignoreCase = true) ->
                    "https://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
                else -> "https://$trimmed"
            }
        }

        fun defaultPresetContainers(): List<WebContainerProfile> = listOf(
            WebContainerProfile(
                id = "stealth_ig_creator_1",
                name = "Instagram Creator #2",
                url = "https://www.instagram.com/",
                platformTag = "Instagram",
                userAgentMode = UserAgentMode.PIXEL_8_PRO,
                fingerprintSeed = "IG84F2A91C0B",
                blockWebRtc = true,
                accentHex = 0xFFEC4899L
            ),
            WebContainerProfile(
                id = "stealth_yt_studio_2",
                name = "YouTube Studio #2",
                url = "https://studio.youtube.com/",
                platformTag = "YouTube",
                userAgentMode = UserAgentMode.DESKTOP_CHROME_MAC,
                fingerprintSeed = "YT39D7E14B6A",
                blockWebRtc = true,
                accentHex = 0xFFF43F5EL
            ),
            WebContainerProfile(
                id = "stealth_x_brand_3",
                name = "X Brand Account #3",
                url = "https://x.com/home",
                platformTag = "X / Social",
                userAgentMode = UserAgentMode.RANDOM_ROTATE,
                fingerprintSeed = "XC71B5F88E2D",
                blockWebRtc = true,
                accentHex = 0xFF06B6D4L
            ),
            WebContainerProfile(
                id = "stealth_tt_creator_4",
                name = "TikTok Creator #4",
                url = "https://www.tiktok.com/",
                platformTag = "TikTok",
                userAgentMode = UserAgentMode.DESKTOP_CHROME_WIN,
                fingerprintSeed = "TT56A0C39F4E",
                blockWebRtc = true,
                accentHex = 0xFF6366F1L
            )
        )
    }
}
