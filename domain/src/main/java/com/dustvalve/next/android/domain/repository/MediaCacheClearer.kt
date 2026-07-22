package com.dustvalve.next.android.domain.repository

/**
 * Clears the streaming media cache (ExoPlayer's on-disk SimpleCache) through
 * the live cache instance so its index stays consistent. Deleting the cache
 * directory out from under an open Media3 SimpleCache desyncs the index and
 * surfaces CacheExceptions on the next playback; the app-layer implementation
 * removes resources via the cache API instead.
 */
interface MediaCacheClearer {

    /**
     * Removes every cached resource. Best-effort per entry; implementations
     * must rethrow [kotlin.coroutines.cancellation.CancellationException] and
     * may throw on wholesale failure - callers treat that as "skip, never
     * delete the directory directly".
     */
    suspend fun clearAll()
}
