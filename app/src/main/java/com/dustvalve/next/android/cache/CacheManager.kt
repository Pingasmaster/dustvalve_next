package com.dustvalve.next.android.cache

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class CacheManager @Inject constructor(
    private val diskCacheManager: DiskCacheManager,
    private val storageTracker: StorageTracker,
    private val evictionPolicy: CacheEvictionPolicy,
    private val simpleCache: SimpleCache,
) {

    /**
     * Caches audio data for the given track ID.
     * Writes the data to disk and registers the entry with the storage tracker.
     * @return The absolute file path of the cached audio file.
     */
    suspend fun cacheAudio(trackId: String, data: ByteArray): String = withContext(Dispatchers.IO) {
        val file = diskCacheManager.writeAudioCache(trackId, data)
        storageTracker.registerCacheEntry(
            key = trackId,
            type = "audio",
            sizeBytes = data.size.toLong(),
            filePath = file.absolutePath,
            isUserDownload = false
        )
        file.absolutePath
    }

    /**
     * Caches image data for the given key.
     * Writes the data to disk and registers the entry with the storage tracker.
     * @return The absolute file path of the cached image file.
     */
    suspend fun cacheImage(key: String, data: ByteArray): String = withContext(Dispatchers.IO) {
        val file = diskCacheManager.writeImageCache(key, data)
        storageTracker.registerCacheEntry(
            key = key,
            type = "image",
            sizeBytes = data.size.toLong(),
            filePath = file.absolutePath,
            isUserDownload = false
        )
        file.absolutePath
    }

    /**
     * Returns the cached audio file for the given track ID, or null if it does not exist.
     */
    suspend fun getCachedAudio(trackId: String): File? = withContext(Dispatchers.IO) {
        val file = diskCacheManager.getAudioCacheFile(trackId)
        if (!file.exists()) return@withContext null
        storageTracker.updateAccess(trackId)
        file
    }

    /**
     * Returns the cached image file for the given key, or null if it does not exist.
     */
    suspend fun getCachedImage(key: String): File? = withContext(Dispatchers.IO) {
        val file = diskCacheManager.getImageCacheFile(key)
        if (!file.exists()) return@withContext null
        storageTracker.updateAccess(key)
        file
    }

    /**
     * Checks the storage tracker to determine if the cache is over the configured limit.
     * If it is, triggers eviction to free only the overage amount.
     */
    suspend fun evictIfNeeded() = withContext(Dispatchers.IO) {
        if (storageTracker.isOverLimit()) {
            val overage = storageTracker.getOverageBytes()
            if (overage > 0L) {
                evictionPolicy.evict(overage)
                storageTracker.notifyCacheChanged()
            }
        }
    }

    /**
     * Clears all cached files (but not user downloads) and resets the storage tracker entries.
     */
    @OptIn(UnstableApi::class)
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        diskCacheManager.clearCacheDir()
        // Clear ExoPlayer's media cache safely through its own API
        val keys = synchronized(simpleCache) { simpleCache.keys.toList() }
        for (key in keys) {
            try {
                simpleCache.removeResource(key)
            } catch (_: Exception) {
                // Skip corrupted cache entries
            }
        }
        // Delete all non-download DB entries in a single SQL statement (files already removed above)
        storageTracker.removeNonDownloadEntries()
    }
}
