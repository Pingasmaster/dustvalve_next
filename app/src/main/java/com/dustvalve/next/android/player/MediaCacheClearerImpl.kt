package com.dustvalve.next.android.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.repository.MediaCacheClearer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Clears ExoPlayer's media cache via the same @Singleton [SimpleCache]
 * instance PlayerModule feeds the player, keeping the cache index coherent.
 * Per-key removal is preferred over release + SimpleCache.delete because the
 * player may hold the cache open concurrently.
 *
 * The cache is injected lazily so constructing this clearer (a
 * DownloadRepositoryImpl dependency created at app start) does not force
 * SimpleCache - and its database - onto the cold-start path.
 */
@Singleton
class MediaCacheClearerImpl @Inject constructor(
    private val simpleCache: dagger.Lazy<SimpleCache>,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : MediaCacheClearer {

    @OptIn(UnstableApi::class)
    @Suppress("TooGenericExceptionCaught")
    override suspend fun clearAll() = withContext(ioDispatcher) {
        val cache = simpleCache.get()
        // Snapshot the key set: removeResource mutates the backing set.
        for (key in cache.keys.toList()) {
            try {
                cache.removeResource(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to remove cached resource $key", e)
            }
        }
    }

    private companion object {
        private const val TAG = "MediaCacheClearer"
    }
}
