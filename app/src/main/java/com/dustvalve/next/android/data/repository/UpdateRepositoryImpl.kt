package com.dustvalve.next.android.data.repository

import android.content.Context
import com.dustvalve.next.android.domain.repository.AppUpdate
import com.dustvalve.next.android.domain.repository.DownloadProgress
import com.dustvalve.next.android.domain.repository.UpdateRepository
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

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
) : UpdateRepository {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var downloadedApkPath: String? = null

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

    override suspend fun checkForUpdate(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null

        val body = response.body.string()
        val releases = json.decodeFromString<List<GitHubRelease>>(body)

        val latest = releases.firstOrNull { release ->
            !release.draft && !release.prerelease &&
                release.assets.any { it.name.endsWith(".apk") }
        } ?: return@withContext null

        val latestVersion = latest.tagName.removePrefix("v")
        if (!isNewer(latestVersion, currentVersion)) return@withContext null

        val apkAsset = latest.assets.first { it.name.endsWith(".apk") }

        AppUpdate(
            versionName = latestVersion,
            releaseNotes = latest.body.ifBlank { latest.name },
            apkDownloadUrl = apkAsset.browserDownloadUrl,
        )
    }

    override fun downloadApk(url: String): Flow<DownloadProgress> = flow {
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
                val buffer = ByteArray(8192)
                var bytesDownloaded = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    coroutineContext.ensureActive()
                    output.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead
                    emit(DownloadProgress(bytesDownloaded, totalBytes))
                }
            }
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        downloadedApkPath = targetFile.absolutePath
    }.flowOn(Dispatchers.IO)

    override fun getDownloadedApkPath(): String? = downloadedApkPath

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/Pingasmaster/dustvalve_next/releases"

        fun isNewer(remote: String, local: String): Boolean {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            return false
        }
    }
}
