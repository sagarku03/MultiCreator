package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseTitle: String,
    val changelog: String,
    val downloadUrl: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val publishedAt: String
)

sealed class InstallDispatchResult {
    data object LaunchedInstaller : InstallDispatchResult()
    data class NeedsUnknownSourcesPermission(val settingsIntent: Intent) : InstallDispatchResult()
    data class Error(val message: String) : InstallDispatchResult()
}

/**
 * Production GitHub Releases In-App Auto-Updater for MultiCreator.
 * Checks `https://api.github.com/repos/{owner}/{repo}/releases/latest`, compares semantic versions,
 * streams the signed APK with real-time progress callbacks, and installs via FileProvider.
 */
class GitHubUpdater(
    private val context: Context,
    private val repoOwner: String = BuildConfig.GITHUB_OWNER,
    private val repoName: String = BuildConfig.GITHUB_REPO
) {

    /**
     * Queries GitHub Releases API for the latest release and returns [UpdateInfo] if a newer
     * semantic version with a valid signed APK asset is available. Returns null if already up to date
     * or if the repository has no published releases yet.
     */
    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val endpoint = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "MultiCreator-Android/$currentVersionName")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseJson = JSONObject(payload)

            if (releaseJson.optBoolean("draft", false)) {
                return@withContext null
            }

            val tagName = releaseJson.optString("tag_name", "").trim()
            val remoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            if (remoteVersion.isEmpty() || !isNewerVersion(remoteVersion, currentVersionName)) {
                return@withContext null
            }

            val assets = releaseJson.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            var apkName = "MultiCreator-v$remoteVersion.apk"
            var apkSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && isTrustedHttpsReleaseUrl(url)) {
                    apkUrl = url
                    apkName = name
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            val validApkUrl = apkUrl ?: return@withContext null
            val releaseTitle = releaseJson.optString("name", "").ifBlank { "MultiCreator v$remoteVersion" }
            val changelog = releaseJson.optString("body", "").trim()
                .ifBlank { "Performance improvements, stability fixes, and updated anti-detect isolation layers." }
            val publishedAt = releaseJson.optString("published_at", "")

            UpdateInfo(
                versionName = remoteVersion,
                tagName = tagName,
                releaseTitle = releaseTitle,
                changelog = changelog,
                downloadUrl = validApkUrl,
                assetName = apkName,
                assetSizeBytes = apkSize,
                publishedAt = publishedAt
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Streams the signed APK from GitHub Releases into the app's external Downloads directory,
     * reporting continuous download progress (0.0f..1.0f) and byte counters on the main thread.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        fileName: String = "MultiCreator-update.apk",
        onProgress: (progress: Float, bytesDownloaded: Long, totalBytes: Long) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isTrustedHttpsReleaseUrl(downloadUrl)) {
            withContext(Dispatchers.Main) {
                onError("Blocked untrusted download URL. Only HTTPS GitHub release assets are permitted.")
            }
            return
        }

        try {
            val downloadedFile = withContext(Dispatchers.IO) {
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                if (!targetDir.exists()) targetDir.mkdirs()

                val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val finalFile = File(targetDir, safeFileName)
                val tempFile = File(targetDir, "$safeFileName.part")
                if (tempFile.exists()) tempFile.delete()

                val connection = openFollowingRedirects(downloadUrl)
                try {
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        throw IllegalStateException("Server returned HTTP $responseCode while downloading APK.")
                    }

                    val totalBytes = connection.contentLengthLong
                    var bytesReadTotal = 0L
                    var lastEmitTimeMs = 0L

                    BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesReadTotal += read

                                val now = System.currentTimeMillis()
                                if (now - lastEmitTimeMs >= PROGRESS_THROTTLE_MS ||
                                    (totalBytes > 0 && bytesReadTotal >= totalBytes)
                                ) {
                                    lastEmitTimeMs = now
                                    val fraction = if (totalBytes > 0L) {
                                        (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    withContext(Dispatchers.Main) {
                                        onProgress(fraction, bytesReadTotal, totalBytes)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (bytesReadTotal <= 0L) {
                        throw IllegalStateException("Downloaded APK file was empty.")
                    }

                    if (finalFile.exists()) finalFile.delete()
                    if (!tempFile.renameTo(finalFile)) {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                    }

                    withContext(Dispatchers.Main) {
                        onProgress(1f, bytesReadTotal, if (totalBytes > 0) totalBytes else bytesReadTotal)
                    }
                    finalFile
                } finally {
                    connection.disconnect()
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(downloadedFile)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: Exception) {
            withContext(Dispatchers.Main) {
                onError(ex.localizedMessage ?: "Failed to download APK update.")
            }
        }
    }

    /**
     * Checks whether the system currently allows MultiCreator to request APK package installations.
     */
    fun canInstallPackages(): Boolean {
        return runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    }

    /**
     * Launches the Android Package Installer for the downloaded APK via a scoped FileProvider URI.
     * If the user has not yet enabled "Install unknown apps" for MultiCreator, returns a settings
     * intent so the caller can guide the user directly to the permission toggle.
     */
    fun installApk(file: File): InstallDispatchResult {
        if (!file.exists() || file.length() == 0L) {
            return InstallDispatchResult.Error("Downloaded APK file is missing or corrupted.")
        }

        if (!canInstallPackages()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return InstallDispatchResult.NeedsUnknownSourcesPermission(settingsIntent)
        }

        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            InstallDispatchResult.LaunchedInstaller
        }.getOrElse { ex ->
            InstallDispatchResult.Error(ex.localizedMessage ?: "Unable to open Android Package Installer.")
        }
    }

    /**
     * Follows up to [MAX_REDIRECTS] HTTPS redirects manually so GitHub asset downloads redirecting
     * to GitHub CDN hosts succeed reliably on all Android versions.
     */
    private fun openFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "MultiCreator-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val nextLocation = conn.getHeaderField("Location")
                conn.disconnect()
                if (nextLocation.isNullOrBlank() || !nextLocation.startsWith("https://", ignoreCase = true)) {
                    throw SecurityException("Refused unsafe redirect while downloading APK.")
                }
                currentUrl = nextLocation
            } else {
                return conn
            }
        }
        throw IllegalStateException("Too many redirects while downloading APK.")
    }

    private fun isTrustedHttpsReleaseUrl(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "github.com" ||
            host == "api.github.com" ||
            host.endsWith(".githubusercontent.com")
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 45_000
        private const val BUFFER_SIZE = 16 * 1024
        private const val PROGRESS_THROTTLE_MS = 80L
        private const val MAX_REDIRECTS = 5

        /**
         * Compares two semantic version strings (e.g. "1.2.0" vs "1.0.4").
         * Returns true strictly when [remoteVersion] is greater than [localVersion].
         */
        fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
            val remoteParts = parseVersionSegments(remoteVersion)
            val localParts = parseVersionSegments(localVersion)
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r != l) return r > l
            }
            return false
        }

        private fun parseVersionSegments(version: String): List<Int> {
            val core = version
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            return core.split('.').map { segment ->
                segment.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
        }
    }
}
