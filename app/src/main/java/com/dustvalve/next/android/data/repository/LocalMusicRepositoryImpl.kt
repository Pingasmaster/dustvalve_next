package com.dustvalve.next.android.data.repository

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.scanner.LocalMusicScanner
import com.dustvalve.next.android.data.local.scanner.ScanResult
import com.dustvalve.next.android.data.local.scanner.LocalMusicSyncWorker
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.core.net.toUri
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scanner: LocalMusicScanner,
    private val settingsDataStore: SettingsDataStore,
    private val trackDao: TrackDao,
) : LocalMusicRepository {

    companion object {
        private const val WORK_NAME = "local_music_sync"
    }

    override suspend fun scan(): ScanResult {
        val folderUriString = settingsDataStore.getLocalMusicFolderUriSync()
            ?: return ScanResult(0, 0, 0)
        val folderUri = folderUriString.toUri()
        return scanner.scan(folderUri)
    }

    override suspend fun clearAll() {
        // Delete all local tracks from DB
        trackDao.deleteAllLocalTracks()

        // Delete cached cover art
        val artDir = File(context.filesDir, "local_art")
        if (artDir.exists()) {
            artDir.deleteRecursively()
        }

        // Release persisted URI permission
        val folderUriString = settingsDataStore.getLocalMusicFolderUriSync()
        if (folderUriString != null) {
            try {
                val uri = folderUriString.toUri()
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Permission may already be released
            }
        }

        // Clear folder URI from settings
        settingsDataStore.setLocalMusicFolderUri(null)
    }

    override suspend fun scheduleSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<LocalMusicSyncWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            flexTimeInterval = 15,
            flexTimeIntervalUnit = TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

    override suspend fun cancelSyncWork() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    override fun getLocalTrackCount(): Flow<Int> {
        return trackDao.getLocalTracks().map { it.size }
    }
}
