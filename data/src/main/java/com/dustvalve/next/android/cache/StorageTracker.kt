package com.dustvalve.next.android.cache

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.CacheInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports the size of the unified downloads pool (DB-tracked downloads +
 * Coil's image directory + ExoPlayer's media_cache) and whether it has
 * exceeded the user-configured storage limit. There is no separate cache
 * vs. downloads bucket; everything is one pool.
 *
 * Download usage sums **existing files on disk**, not the cached
 * [DownloadEntity.sizeBytes] column, so phantom rows (missing files) cannot
 * inflate the meter or drive eviction.
 */
@Singleton
class StorageTracker(
    private val downloadDao: DownloadDao,
    private val settingsDataStore: SettingsDataStore,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject constructor(
        gateway: DatabaseGateway,
        settingsDataStore: SettingsDataStore,
        @ApplicationContext context: Context,
        @Dispatcher(AppDispatchers.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(gateway.downloadDao, settingsDataStore, context, ioDispatcher)

    private val _sizeUpdateTrigger = MutableStateFlow(0L)

    fun notifyChanged() {
        _sizeUpdateTrigger.update { it + 1 }
    }

    fun getCacheInfo(): Flow<CacheInfo> = combine(_sizeUpdateTrigger, settingsDataStore.storageLimit) { _, limitBytes ->
        val (totalDownloads, pinnedSize) = measureOnDiskDownloads(downloadDao.getAllSync())
        val unpinnedAudioSize = (totalDownloads - pinnedSize).coerceAtLeast(0L)
        val imagesSize = StoragePaths.calculateDirSize(StoragePaths.imagesDir(context)) +
            StoragePaths.calculateDirSize(StoragePaths.coversDir(context))
        val mediaCacheSize = StoragePaths.calculateDirSize(StoragePaths.mediaCacheDir(context))

        val totalSize = totalDownloads + imagesSize + mediaCacheSize
        val usagePercent = when {
            limitBytes <= 0L -> 0f
            limitBytes == Long.MAX_VALUE -> 0f
            else -> (totalSize.toFloat() / limitBytes.toFloat() * 100f).coerceIn(0f, 100f)
        }

        CacheInfo(
            totalSizeBytes = totalSize,
            limitBytes = limitBytes,
            audioSizeBytes = unpinnedAudioSize + mediaCacheSize,
            imageSizeBytes = imagesSize,
            downloadSizeBytes = pinnedSize,
            usagePercent = usagePercent,
            freeSpaceBytes = StatFs(Environment.getDataDirectory().path).availableBytes,
        )
    }.flowOn(ioDispatcher)

    /** How many bytes the pool exceeds the configured limit by, or 0 if within limit. */
    suspend fun getOverageBytes(): Long = withContext(ioDispatcher) {
        val limit = settingsDataStore.getStorageLimitSync()
        if (limit == Long.MAX_VALUE) return@withContext 0L
        (getEffectiveTotalSize() - limit).coerceAtLeast(0L)
    }

    private suspend fun getEffectiveTotalSize(): Long {
        val downloads = measureOnDiskDownloads(downloadDao.getAllSync()).first
        val images = StoragePaths.calculateDirSize(StoragePaths.imagesDir(context)) +
            StoragePaths.calculateDirSize(StoragePaths.coversDir(context))
        val media = StoragePaths.calculateDirSize(StoragePaths.mediaCacheDir(context))
        return downloads + images + media
    }

    companion object {
        /**
         * Sums lengths of files that still exist. Missing / blank paths
         * contribute 0 so phantom [DownloadEntity.sizeBytes] cannot skew
         * usage or overage math.
         *
         * @return Pair(totalBytes, pinnedBytes)
         */
        fun measureOnDiskDownloads(rows: List<DownloadEntity>): Pair<Long, Long> {
            var total = 0L
            var pinned = 0L
            for (row in rows) {
                val len = onDiskBytes(row.filePath)
                if (len <= 0L) continue
                total += len
                if (row.pinned) pinned += len
            }
            return total to pinned
        }

        fun onDiskBytes(path: String): Long {
            if (path.isBlank()) return 0L
            val file = File(path)
            return if (file.isFile) file.length() else 0L
        }
    }
}
