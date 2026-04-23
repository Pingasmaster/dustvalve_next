package com.dustvalve.next.android.cache

import android.content.Context
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.domain.model.CacheInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports the size of the unified downloads pool (DB-tracked downloads +
 * Coil's image directory + ExoPlayer's media_cache) and whether it has
 * exceeded the user-configured storage limit. There is no separate cache
 * vs. downloads bucket; everything is one pool.
 */
@Singleton
class StorageTracker @Inject constructor(
    private val downloadDao: DownloadDao,
    private val settingsDataStore: SettingsDataStore,
    @param:ApplicationContext private val context: Context,
) {

    enum class WarningLevel { NONE, WARNING, CRITICAL }

    private val _sizeUpdateTrigger = MutableStateFlow(0L)

    fun notifyChanged() {
        _sizeUpdateTrigger.update { it + 1 }
    }

    fun getCacheInfo(): Flow<CacheInfo> {
        return combine(_sizeUpdateTrigger, settingsDataStore.storageLimit) { _, limitBytes ->
            val pinnedSize = downloadDao.getPinnedSize()
            val totalDownloads = downloadDao.getTotalSize()
            val unpinnedAudioSize = (totalDownloads - pinnedSize).coerceAtLeast(0L)
            val imagesSize = StoragePaths.calculateDirSize(StoragePaths.imagesDir(context))
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
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun isOverLimit(): Boolean = withContext(Dispatchers.IO) {
        val limit = settingsDataStore.getStorageLimitSync()
        if (limit == Long.MAX_VALUE) return@withContext false
        getEffectiveTotalSize() > limit
    }

    /** How many bytes the pool exceeds the configured limit by, or 0 if within limit. */
    suspend fun getOverageBytes(): Long = withContext(Dispatchers.IO) {
        val limit = settingsDataStore.getStorageLimitSync()
        if (limit == Long.MAX_VALUE) return@withContext 0L
        (getEffectiveTotalSize() - limit).coerceAtLeast(0L)
    }

    private suspend fun getEffectiveTotalSize(): Long {
        val downloads = downloadDao.getTotalSize()
        val images = StoragePaths.calculateDirSize(StoragePaths.imagesDir(context))
        val media = StoragePaths.calculateDirSize(StoragePaths.mediaCacheDir(context))
        return downloads + images + media
    }

    suspend fun getWarningLevel(): WarningLevel = withContext(Dispatchers.IO) {
        val limit = settingsDataStore.getStorageLimitSync()
        if (limit <= 0L || limit == Long.MAX_VALUE) return@withContext WarningLevel.NONE
        val usage = getEffectiveTotalSize().toFloat() / limit.toFloat()
        when {
            usage >= 1.0f -> WarningLevel.CRITICAL
            usage >= 0.85f -> WarningLevel.WARNING
            else -> WarningLevel.NONE
        }
    }
}
