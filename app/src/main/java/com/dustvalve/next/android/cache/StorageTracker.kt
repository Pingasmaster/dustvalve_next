package com.dustvalve.next.android.cache

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.CacheEntryDao
import com.dustvalve.next.android.data.local.db.entity.CacheEntryEntity
import com.dustvalve.next.android.domain.model.CacheInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageTracker @Inject constructor(
    private val cacheEntryDao: CacheEntryDao,
    private val settingsDataStore: SettingsDataStore,
    private val diskCacheManager: DiskCacheManager
) {

    enum class WarningLevel { NONE, WARNING, CRITICAL }

    private val _sizeUpdateTrigger = MutableStateFlow(0L)

    suspend fun registerCacheEntry(
        key: String,
        type: String,
        sizeBytes: Long,
        filePath: String?,
        isUserDownload: Boolean = false
    ) {
        val entity = CacheEntryEntity(
            key = key,
            type = type,
            sizeBytes = sizeBytes,
            lastAccessed = System.currentTimeMillis(),
            isUserDownload = isUserDownload,
            filePath = filePath
        )
        cacheEntryDao.insert(entity)
        _sizeUpdateTrigger.update { it + 1 }
    }

    suspend fun updateAccess(key: String) {
        cacheEntryDao.updateLastAccessed(key, System.currentTimeMillis())
    }

    suspend fun removeEntry(key: String) {
        cacheEntryDao.deleteByKey(key)
        _sizeUpdateTrigger.update { it + 1 }
    }

    suspend fun removeNonDownloadEntries() {
        cacheEntryDao.deleteNonDownloads()
        _sizeUpdateTrigger.update { it + 1 }
    }

    fun notifyCacheChanged() {
        _sizeUpdateTrigger.update { it + 1 }
    }

    fun getTotalSize(): Flow<Long> {
        return combine(_sizeUpdateTrigger, settingsDataStore.storageLimit) { _, _ ->
            cacheEntryDao.getTotalSize()
        }.flowOn(Dispatchers.IO)
    }

    fun getCacheInfo(): Flow<CacheInfo> {
        return combine(_sizeUpdateTrigger, settingsDataStore.storageLimit) { _, limitBytes ->
            val audioEntries = cacheEntryDao.getByType("audio")
            val imageEntries = cacheEntryDao.getByType("image")
            val downloadEntries = cacheEntryDao.getAll().filter { it.isUserDownload }

            val audioSize = audioEntries.sumOf { it.sizeBytes }
            val imageSize = imageEntries.sumOf { it.sizeBytes }
            val downloadSize = downloadEntries.sumOf { it.sizeBytes }

            // Include ExoPlayer's media_cache in the reported total
            val mediaCacheSize = diskCacheManager.calculateDirSize(diskCacheManager.getMediaCacheDir())

            // Use non-download total for consistency with isOverLimit()/getOverageBytes(),
            // which exclude user downloads since they are never evicted.
            val evictableSize = cacheEntryDao.getNonDownloadTotalSize() + mediaCacheSize
            val usagePercent = when {
                limitBytes <= 0L -> 0f // cache disabled
                limitBytes == Long.MAX_VALUE -> 0f // unlimited
                else -> (evictableSize.toFloat() / limitBytes.toFloat() * 100f).coerceIn(0f, 100f)
            }

            CacheInfo(
                totalSizeBytes = evictableSize + downloadSize,
                limitBytes = limitBytes,
                audioSizeBytes = audioSize,
                imageSizeBytes = imageSize,
                downloadSizeBytes = downloadSize,
                usagePercent = usagePercent
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun isOverLimit(): Boolean = withContext(Dispatchers.IO) {
        val limit = settingsDataStore.getStorageLimitSync()
        if (limit == Long.MAX_VALUE) return@withContext false
        val totalSize = getEffectiveTotalSize()
        totalSize > limit
    }

    /**
     * Returns how many bytes the cache exceeds the configured limit by, or 0 if within limit.
     */
    suspend fun getOverageBytes(): Long = withContext(Dispatchers.IO) {
        val limit = settingsDataStore.getStorageLimitSync()
        if (limit == Long.MAX_VALUE) return@withContext 0L
        val totalSize = getEffectiveTotalSize()
        (totalSize - limit).coerceAtLeast(0L)
    }

    /**
     * Returns the evictable cache size (excluding user downloads) plus ExoPlayer's media_cache.
     */
    private suspend fun getEffectiveTotalSize(): Long {
        val dbSize = cacheEntryDao.getNonDownloadTotalSize()
        val mediaCacheSize = diskCacheManager.calculateDirSize(diskCacheManager.getMediaCacheDir())
        return dbSize + mediaCacheSize
    }

    suspend fun getWarningLevel(): WarningLevel = withContext(Dispatchers.IO) {
        val limit = settingsDataStore.getStorageLimitSync()
        if (limit <= 0L || limit == Long.MAX_VALUE) return@withContext WarningLevel.NONE

        val totalSize = getEffectiveTotalSize()
        val usagePercent = totalSize.toFloat() / limit.toFloat()
        when {
            usagePercent >= 1.0f -> WarningLevel.CRITICAL
            usagePercent >= 0.85f -> WarningLevel.WARNING
            else -> WarningLevel.NONE
        }
    }
}
