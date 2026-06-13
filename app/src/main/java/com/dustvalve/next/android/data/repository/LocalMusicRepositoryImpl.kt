package com.dustvalve.next.android.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.scanner.LocalMusicScanner
import com.dustvalve.next.android.data.local.scanner.LocalMusicSyncWorker
import com.dustvalve.next.android.data.local.scanner.MediaStoreScanner
import com.dustvalve.next.android.data.local.scanner.ScanResult
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scanner: LocalMusicScanner,
    private val mediaStoreScanner: MediaStoreScanner,
    private val settingsDataStore: SettingsDataStore,
    private val trackDao: TrackDao,
) : LocalMusicRepository {

    companion object {
        private const val WORK_NAME = "local_music_sync"
    }

    override suspend fun scan(): ScanResult {
        if (settingsDataStore.getLocalMusicUseMediaStoreSync()) {
            return mediaStoreScanner.scan()
        }

        val folderUris = settingsDataStore.getLocalMusicFolderUrisSync()
        if (folderUris.isEmpty()) return ScanResult(0, 0, 0)

        var totalAdded = 0
        var totalRemoved = 0
        var totalCount = 0
        for (uriString in folderUris) {
            val result = scanner.scan(uriString.toUri())
            totalAdded += result.added
            totalRemoved += result.removed
            totalCount += result.total
        }
        return ScanResult(added = totalAdded, removed = totalRemoved, total = totalCount)
    }

    override suspend fun addFolder(uri: String) {
        settingsDataStore.addLocalMusicFolderUri(uri)
    }

    override suspend fun removeFolder(uri: String) {
        // Get track IDs for cover art cleanup
        val trackIds = trackDao.getLocalTrackIdsByFolderSync(uri)

        // Delete tracks belonging to this folder
        trackDao.deleteLocalTracksByFolder(uri)

        // Clean up cached cover art
        trackIds.forEach { id ->
            val artFile = File(context.filesDir, "local_art/$id.jpg")
            artFile.delete()
        }

        // Release SAF permission
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Permission may already be released
        }

        // Remove from settings
        settingsDataStore.removeLocalMusicFolderUri(uri)
    }

    override suspend fun clearAll() {
        // Delete all local tracks from DB
        trackDao.deleteAllLocalTracks()

        // Delete cached cover art
        val artDir = File(context.filesDir, "local_art")
        if (artDir.exists()) {
            artDir.deleteRecursively()
        }

        // Release all persisted URI permissions
        for (uriString in settingsDataStore.getLocalMusicFolderUrisSync()) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uriString.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Permission may already be released
            }
        }

        // Clear all folder URIs from settings
        settingsDataStore.setLocalMusicFolderUris(emptyList())
    }

    override suspend fun scheduleSyncWork() {
        // Local-library changes rarely warrant sub-hour cadence. A 6h interval
        // with 2h flex lets the OS batch this with other apps' idle work, and
        // requires-device-idle keeps it off the user's hot path entirely.
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<LocalMusicSyncWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 2,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        // UPDATE (not KEEP) so the new schedule replaces any existing
        // 30-minute schedule still pinned in a previously installed build.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest,
        )
    }

    override suspend fun cancelSyncWork() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
