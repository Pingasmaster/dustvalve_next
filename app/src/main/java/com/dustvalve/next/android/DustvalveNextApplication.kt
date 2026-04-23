package com.dustvalve.next.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.download.AutoDownloadFavoritesCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class DustvalveNextApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var autoDownloadFavoritesCoordinator: AutoDownloadFavoritesCoordinator

    override fun onCreate() {
        super.onCreate()
        // Idempotent — observes the "Auto-download favorites" toggle and
        // enqueues downloads for any favorited tracks not already on disk.
        autoDownloadFavoritesCoordinator.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoilEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            CoilEntryPoint::class.java,
        )
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = entryPoint.okHttpClient()))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                // Coil's image cache lives inside the unified downloads pool
                // (filesDir/downloads/images) so its size shows up in the
                // single storage indicator and "Remove all downloads" wipes
                // it alongside audio. Coil manages its own LRU within the
                // configured maxSizeBytes; pinned audio downloads are
                // tracked in Room and not affected by Coil.
                DiskCache.Builder()
                    .directory(StoragePaths.imagesDir(this))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
