package com.dustvalve.next.android.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dustvalve.next.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Lightweight self-update for pre-alpha. Polls the GitHub releases API,
 * compares the latest tag to [BuildConfig.VERSION_NAME], and (when newer)
 * downloads the APK + hands it to the system installer via FileProvider.
 *
 * Pre-alpha policy: this is the ONLY self-update path; nothing runs at app
 * startup. The user opts in from Settings → About → "Search for updates".
 */
@Singleton
class AppUpdateService @Inject constructor(
    private val client: OkHttpClient,
    @param:ApplicationContext private val context: Context,
) {

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
    )

    data class AvailableUpdate(
        val versionName: String,
        val apkDownloadUrl: String,
    )

    data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
        val fraction: Float get() = if (totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    /**
     * @return the newer [AvailableUpdate], or null if the latest release on
     *         GitHub is the same or older than the installed build.
     * @throws IOException on network or parse failure.
     */
    suspend fun checkForUpdate(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}")
        }
        val body = response.body.string()
        val releases = json.decodeFromString<List<GitHubRelease>>(body)

        val latest = releases.firstOrNull { release ->
            !release.draft && !release.prerelease &&
                release.assets.any { it.name.endsWith(".apk") }
        } ?: return@withContext null

        val latestVersion = latest.tagName.removePrefix("v")
        if (!isNewer(latestVersion, BuildConfig.VERSION_NAME)) return@withContext null

        val apkAsset = latest.assets.first { it.name.endsWith(".apk") }
        AvailableUpdate(versionName = latestVersion, apkDownloadUrl = apkAsset.browserDownloadUrl)
    }

    /**
     * Streams the APK to `cacheDir/updates/update.apk` and emits progress.
     * The flow completes once the file is fully written.
     */
    fun downloadApk(url: String): Flow<DownloadProgress> = flow {
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

        val response = call.execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

        val totalBytes = response.header("Content-Length")?.toLongOrNull() ?: -1L
        tempFile.outputStream().use { output ->
            response.body.byteStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    coroutineContext.ensureActive()
                    output.write(buffer, 0, read)
                    downloaded += read
                    emit(DownloadProgress(downloaded, totalBytes))
                }
            }
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }.flowOn(Dispatchers.IO)

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
        context.startActivity(intent)
    }

    companion object {
        const val REPO_URL = "https://github.com/Pingasmaster/dustvalve_next"
        private const val RELEASES_URL =
            "https://api.github.com/repos/Pingasmaster/dustvalve_next/releases"

        /** True when [remote] is a strictly higher dotted-int version than [local]. */
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val l = local.split(".").map { it.toIntOrNull() ?: 0 }
            val n = maxOf(r.size, l.size)
            for (i in 0 until n) {
                val ri = r.getOrElse(i) { 0 }
                val li = l.getOrElse(i) { 0 }
                if (ri > li) return true
                if (ri < li) return false
            }
            return false
        }
    }
}
