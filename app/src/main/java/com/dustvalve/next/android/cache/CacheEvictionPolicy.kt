package com.dustvalve.next.android.cache

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.CacheEntryDao
import com.dustvalve.next.android.data.local.db.entity.CacheEntryEntity
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheEvictionPolicy @Inject constructor(
    private val database: DustvalveNextDatabase,
    private val cacheEntryDao: CacheEntryDao,
    private val diskCacheManager: DiskCacheManager,
    private val settingsDataStore: SettingsDataStore
) {

    /**
     * Evicts cache entries using an LRU strategy until at least [targetBytes] have been freed.
     *
     * Eviction order:
     * 1. Auto-cached audio (oldest accessed first)
     * 2. Cached images
     * 3. Stale metadata
     * Never evicts user downloads (isUserDownload = true).
     */
    suspend fun evict(targetBytes: Long) {
        if (targetBytes <= 0L) return

        var freedBytes = 0L

        // Get all eviction candidates ordered by lastAccessed ASC, excluding user downloads
        val candidates = cacheEntryDao.getEvictionCandidates()

        // Collect entries to evict across all phases
        val toEvict = mutableListOf<CacheEntryEntity>()

        // Phase 1: Auto-cached audio
        val audioCandidates = candidates.filter { it.type == "audio" }
        for (entry in audioCandidates) {
            if (freedBytes >= targetBytes) break
            toEvict.add(entry)
            freedBytes += entry.sizeBytes
        }

        // Phase 2: Cached images
        if (freedBytes < targetBytes) {
            val imageCandidates = candidates.filter { it.type == "image" }
            for (entry in imageCandidates) {
                if (freedBytes >= targetBytes) break
                toEvict.add(entry)
                freedBytes += entry.sizeBytes
            }
        }

        // Phase 3: Stale metadata
        if (freedBytes < targetBytes) {
            val metadataCandidates = candidates.filter { it.type == "metadata" }
            for (entry in metadataCandidates) {
                if (freedBytes >= targetBytes) break
                toEvict.add(entry)
                freedBytes += entry.sizeBytes
            }
        }

        if (toEvict.isEmpty()) return

        // Delete DB entries first in a transaction, then delete files.
        // A crash after DB deletion leaves orphaned files (wasted disk) rather than
        // orphaned DB entries (which would inflate reported cache sizes).
        database.withTransaction {
            for (entry in toEvict) {
                cacheEntryDao.deleteByKey(entry.key)
            }
        }

        // Delete files (best-effort)
        for (entry in toEvict) {
            entry.filePath?.let { path ->
                try {
                    diskCacheManager.deleteFile(path)
                } catch (_: Exception) {
                    android.util.Log.w("CacheEviction", "Failed to delete file: ${entry.filePath}")
                }
            }
        }
    }

    /**
     * Returns the total size of all non-download cache entries that could be evicted.
     */
    suspend fun getEvictableSize(): Long {
        val candidates = cacheEntryDao.getEvictionCandidates()
        return candidates.sumOf { it.sizeBytes }
    }

}
