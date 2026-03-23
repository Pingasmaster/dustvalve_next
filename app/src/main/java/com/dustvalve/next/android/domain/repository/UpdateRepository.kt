package com.dustvalve.next.android.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
)

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    val fraction: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}

interface UpdateRepository {
    suspend fun checkForUpdate(currentVersion: String): AppUpdate?
    fun downloadApk(url: String): Flow<DownloadProgress>
    fun getDownloadedApkPath(): String?
}
