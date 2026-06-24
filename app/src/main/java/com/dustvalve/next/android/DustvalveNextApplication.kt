package com.dustvalve.next.android

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.data.storage.folder.FolderMirror
import com.dustvalve.next.android.download.AutoDownloadFavoritesCoordinator
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.download.DownloadNotificationCenter
import com.dustvalve.next.android.update.AppUpdateController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class DustvalveNextApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var autoDownloadFavoritesCoordinator: AutoDownloadFavoritesCoordinator

    @Inject
    lateinit var folderMirror: FolderMirror

    @Inject
    lateinit var appUpdateController: AppUpdateController

    @Inject
    lateinit var downloadNotificationCenter: DownloadNotificationCenter

    @Inject
    lateinit var downloadController: DownloadController

    override fun onCreate() {
        super.onCreate()
        downloadNotificationCenter.ensureChannel()
        // Drop partial .tmp files orphaned by a previous process death; the
        // in-memory download queue that could have resumed them is gone.
        downloadController.purgeStalePartialsOnColdStart()
        // Idempotent — observes the "Auto-download favorites" toggle and
        // enqueues downloads for any favorited tracks not already on disk.
        autoDownloadFavoritesCoordinator.start()
        // Observes the dedicated-folder toggle; when on, mirrors every user-
        // data table + DataStore to the folder. Cancels cleanly when off.
        folderMirror.start()
        // Fire-and-forget pre-alpha update check. One-shot per process; the
        // controller swallows errors + mutates shared state that
        // MainActivity's dialog host observes. See AppUpdateController.
        appUpdateController.checkSilently()
    }

    /**
     * Voluntarily release caches the OS can regenerate cheaply. Per the
     * Android 17 memory-efficiency guidance, focus on the two levels the OS
     * raises when the UI is no longer visible: TRIM_MEMORY_UI_HIDDEN and
     * TRIM_MEMORY_BACKGROUND. We drop Coil's in-memory bitmap cache (the
     * biggest ephemeral allocation, bounded to 25 % of available memory in
     * [newImageLoader]) and the in-memory "update available" snapshot held
     * by [appUpdateController]. The Coil disk cache, the OkHttp HTTP cache,
     * and the download controller's queue are intentionally untouched —
     * they're either persistent (disk cache) or resumable across the next
     * foreground (downloads, which run under a foreground service).
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            -> {
                SingletonImageLoader.get(this).memoryCache?.clear()
                appUpdateController.releaseOnTrim()
            }
        }
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

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            CoilEntryPoint::class.java,
        )
        return ImageLoader.Builder(context)
            .components {
                // Honor server Cache-Control on image responses so the OkHttp
                // disk Cache (NetworkModule) and Coil's own disk cache both
                // see consistent freshness rather than diverging.
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { entryPoint.okHttpClient() },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
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
