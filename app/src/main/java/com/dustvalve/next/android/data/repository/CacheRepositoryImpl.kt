package com.dustvalve.next.android.data.repository

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.withTransaction
import com.dustvalve.next.android.cache.CacheEvictionPolicy
import com.dustvalve.next.android.cache.DiskCacheManager
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.CacheEntryDao
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.repository.CacheRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class CacheRepositoryImpl @Inject constructor(
    private val database: DustvalveNextDatabase,
    private val cacheEntryDao: CacheEntryDao,
    private val settingsDataStore: SettingsDataStore,
    private val storageTracker: StorageTracker,
    private val diskCacheManager: DiskCacheManager,
    private val simpleCache: SimpleCache,
    private val evictionPolicy: CacheEvictionPolicy,
) : CacheRepository {

    override fun getCacheInfo(): Flow<CacheInfo> {
        return storageTracker.getCacheInfo()
    }

    @OptIn(UnstableApi::class)
    override suspend fun clearCache() {
        val candidates = cacheEntryDao.getEvictionCandidates()
        // Delete files first (best-effort)
        for (entry in candidates) {
            entry.filePath?.let { path ->
                try {
                    diskCacheManager.deleteFile(path)
                } catch (_: Exception) {
                    // Best-effort file deletion
                }
            }
        }
        // Delete all non-download DB entries in a single SQL statement
        cacheEntryDao.deleteNonDownloads()
        // Also clear ExoPlayer's media cache safely through its own API
        val keys = synchronized(simpleCache) { simpleCache.keys.toList() }
        for (key in keys) {
            try {
                simpleCache.removeResource(key)
            } catch (_: Exception) {
                // Skip corrupted cache entries to allow clearing remaining ones
            }
        }
        storageTracker.notifyCacheChanged()
    }

    override suspend fun setStorageLimit(bytes: Long) {
        settingsDataStore.setStorageLimit(bytes)
    }

    override suspend fun getStorageLimit(): Long {
        return settingsDataStore.getStorageLimitSync()
    }

    override suspend fun evictIfNeeded() {
        val overage = storageTracker.getOverageBytes()
        if (overage > 0L) {
            evictionPolicy.evict(overage)
            storageTracker.notifyCacheChanged()
        }
    }
}
