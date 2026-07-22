package com.dustvalve.next.android.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.FileProvider
import com.dustvalve.next.android.BuildConfig
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.di.qualifiers.MediaHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Lightweight self-update for pre-alpha. Polls the GitHub releases API,
 * compares the latest tag to [BuildConfig.VERSION_NAME], and (when newer)
 * downloads the APK + hands it to the system installer via FileProvider.
 *
 * Pre-alpha policy: this is the ONLY self-update mechanism (no Play Store, no
 * WorkManager job). It runs on a silent cold-start check fired from
 * [com.dustvalve.next.android.DustvalveNextApplication.onCreate] via
 * [AppUpdateController.checkSilently], and on the manual "Search for updates"
 * button in Settings -> About. The cold-start check can be turned off with the
 * "Automatic update checks" toggle (Settings -> About); the manual button never.
 */
@Singleton
open class AppUpdateService @Inject constructor(
    // MediaHttp: no callTimeout - the APK download outlives the base client's
    // 30s whole-call cap on any real-world connection.
    @param:MediaHttp private val client: OkHttpClient,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Overridable in tests so MockWebServer can answer the releases GET.
     * Production is the GitHub Releases listing - returned in reverse
     * chronological order (newest first) by the API.
     */
    protected open val releasesUrl: String =
        "https://api.github.com/repos/Pingasmaster/dustvalve_next/releases"

    /**
     * Overridable in tests so we can pretend the installed build is an
     * arbitrary version. Production reads `BuildConfig.VERSION_NAME`.
     */
    protected open val installedVersion: String = BuildConfig.VERSION_NAME

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        val assets: List<GitHubAsset> = emptyList(),
        val prerelease: Boolean = false,
        val draft: Boolean = false,
    )

    @Serializable
    private data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        /** GitHub asset checksum, "sha256:<hex>" on modern API responses. Absent on older assets. */
        val digest: String? = null,
    )

    data class AvailableUpdate(
        val versionName: String,
        val apkDownloadUrl: String,
        /** The GitHub release body (Markdown), shown verbatim in the update dialog. Empty when none. */
        val releaseNotes: String,
        /** Lowercase-hex SHA-256 of the APK from the asset "digest" field, or null when GitHub sent none. */
        val apkSha256: String? = null,
    )

    data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
        val fraction: Float get() = if (totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * @return the newer [AvailableUpdate], or null if the latest release on
     *         GitHub is the same or older than the installed build.
     * @throws IOException on network or parse failure.
     */
    open suspend fun checkForUpdate(): AvailableUpdate? = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(releasesUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            response.body.string()
        }
        val releases = json.decodeFromString<List<GitHubRelease>>(body)

        // Pre-alpha: every CI build ships as a GitHub prerelease, so we
        // MUST include them here. Drafts (unpublished) are still skipped.
        // Each release ships TWO apks: dustvalve_next.apk (legacy-android8,
        // Android 8-16) and dustvalve_next-future.apk (this modern build,
        // Android 17). Match ONLY the future asset so Android 17 installs
        // never download the legacy APK. Releases without the future asset
        // are skipped: nothing to install.
        val latest = releases.firstOrNull { release ->
            !release.draft && release.assets.any { it.name == FUTURE_APK_ASSET }
        } ?: return@withContext null

        val latestVersion = latest.tagName.removePrefix("v")
        if (!isNewer(latestVersion, installedVersion)) return@withContext null

        val apkAsset = latest.assets.first { it.name == FUTURE_APK_ASSET }
        AvailableUpdate(
            versionName = latestVersion,
            apkDownloadUrl = apkAsset.browserDownloadUrl,
            releaseNotes = latest.body.trim(),
            apkSha256 = parseSha256Digest(apkAsset.digest),
        )
    }

    /**
     * Overridable in tests so MockWebServer (http://localhost) can serve the
     * APK body. Production only ever fetches from https GitHub-owned hosts -
     * see [isGitHubAssetUrl].
     */
    protected open fun isTrustedDownloadUrl(url: String): Boolean = isGitHubAssetUrl(url)

    /**
     * Streams the APK to `cacheDir/updates/update.apk` and emits progress.
     * The flow completes once the file is fully written and verified:
     * against [expectedSha256] when the release asset carried a digest,
     * otherwise (at minimum) against the Content-Length byte count. On any
     * verification failure the temp file is deleted and the flow throws.
     */
    fun downloadApk(url: String, expectedSha256: String? = null): Flow<DownloadProgress> = flow {
        requireTrustedDownloadUrl(url)
        val updateDir = File(context.cacheDir, "updates")
        if (!updateDir.exists()) updateDir.mkdirs()
        updateDir.listFiles()?.forEach { it.delete() }

        val tempFile = File(updateDir, "update.apk.tmp")
        val targetFile = File(updateDir, "update.apk")

        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }

        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val totalBytes = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val sha256 = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            tempFile.outputStream().use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        sha256.update(buffer, 0, read)
                        downloaded += read
                        emit(DownloadProgress(downloaded, totalBytes))
                    }
                }
            }
            verifyDownload(tempFile, sha256.digest(), expectedSha256, downloaded, totalBytes)
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }.flowOn(ioDispatcher)

    private fun requireTrustedDownloadUrl(url: String) {
        if (!isTrustedDownloadUrl(url)) {
            throw IOException("refusing APK download from untrusted URL: $url")
        }
    }

    /**
     * Post-download integrity gate: SHA-256 against the release asset digest
     * when GitHub provided one, byte count against Content-Length otherwise.
     * Deletes [tempFile] and throws on any mismatch.
     */
    private fun verifyDownload(tempFile: File, actualSha256: ByteArray, expectedSha256: String?, downloaded: Long, totalBytes: Long) {
        if (expectedSha256 != null) {
            val actual = actualSha256.joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                throw IOException("APK SHA-256 mismatch: expected $expectedSha256, got $actual")
            }
        } else if (totalBytes > 0L && downloaded != totalBytes) {
            tempFile.delete()
            throw IOException("truncated APK download: got $downloaded of $totalBytes bytes")
        }
    }

    /**
     * Hands the downloaded APK to the system installer via FileProvider.
     * Throws if the file is missing.
     */
    fun launchInstaller() {
        val apk = File(File(context.cacheDir, "updates"), "update.apk")
        if (!apk.exists()) throw IOException("APK not downloaded")
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        // Pin the handoff to the platform package installer. An unpinned
        // implicit VIEW + package-archive intent (with a read grant riding
        // along) is hijackable by any app registering that filter.
        val installerPackage = resolveSystemInstallerPackage(intent)
        if (installerPackage != null) {
            intent.setPackage(installerPackage)
        } else {
            Log.w(TAG, "no system package installer resolved; falling back to unpinned intent")
        }
        context.startActivity(intent)
    }

    /**
     * Package name of a system-app activity handling [intent], or null when
     * none resolves. Best-effort by design: package visibility filtering
     * (no `<queries>` declared) may hide the installer from the query, in
     * which case the caller falls back to the pre-existing unpinned intent.
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun resolveSystemInstallerPackage(intent: Intent): String? = context.packageManager
        .queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        .firstOrNull { resolved ->
            val app = resolved.activityInfo?.applicationInfo ?: return@firstOrNull false
            app.flags and ApplicationInfo.FLAG_SYSTEM != 0
        }
        ?.activityInfo?.packageName

    /** GitHub asset "digest" is "sha256:<hex>" when present; anything else yields null. */
    private fun parseSha256Digest(digest: String?): String? {
        if (digest == null || !digest.startsWith("sha256:")) return null
        val hex = digest.removePrefix("sha256:").lowercase()
        return hex.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
    }

    companion object {
        private const val TAG = "AppUpdate"

        const val REPO_URL = "https://github.com/Pingasmaster/dustvalve_next"

        /** GitHub-release asset name produced by the `build-modern` workflow job (master -> Android 17 build). */
        const val FUTURE_APK_ASSET = "dustvalve_next-future.apk"

        /**
         * True only for https URLs on GitHub-owned hosts. browser_download_url
         * comes back verbatim from the releases API; never fetch an APK from
         * anywhere else, no matter what the JSON says.
         */
        fun isGitHubAssetUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (!parsed.isHttps) return false
            val host = parsed.host
            return host == "github.com" ||
                host.endsWith(".github.com") ||
                host == "objects.githubusercontent.com" ||
                host == "release-assets.githubusercontent.com"
        }

        /**
         * True when [remote] is a strictly higher dotted version than [local].
         * Each component compares by its leading digit run ("2-hotfix" -> 2)
         * so suffixed tags still order correctly. A remote component with no
         * leading digits is unparseable: log and explicitly treat the whole
         * remote as not-newer rather than silently coercing it to 0.
         */
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.split(".").map { component ->
                val value = component.takeWhile { it.isDigit() }.toLongOrNull()
                if (value == null) {
                    Log.w(TAG, "unparseable remote version \"$remote\" (component \"$component\"); treating as not newer")
                    return false
                }
                value
            }
            val l = local.split(".").map { component -> component.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L }
            val n = maxOf(r.size, l.size)
            for (i in 0 until n) {
                val ri = r.getOrElse(i) { 0L }
                val li = l.getOrElse(i) { 0L }
                if (ri > li) return true
                if (ri < li) return false
            }
            return false
        }
    }
}
