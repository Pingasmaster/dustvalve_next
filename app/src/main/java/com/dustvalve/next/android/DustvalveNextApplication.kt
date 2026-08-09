package com.dustvalve.next.android

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.StrictMode
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
import com.dustvalve.next.android.crash.CrashReportManager
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.download.AutoDownloadFavoritesCoordinator
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.download.DownloadNotificationCenter
import com.dustvalve.next.android.ui.image.ThumbnailCanonicalInterceptor
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
    lateinit var appUpdateController: AppUpdateController

    @Inject
    lateinit var downloadNotificationCenter: DownloadNotificationCenter

    @Inject
    lateinit var downloadController: DownloadController

    @Inject
    lateinit var crashReportManager: CrashReportManager

    override fun onCreate() {
        super.onCreate()
        // Plant the uncaught-exception hook before any other init so a crash
        // anywhere below is captured. Purely local: the log is written to
        // filesDir and shown to the user on the next launch; nothing is ever
        // transmitted unless they explicitly share it (see CrashReportManager).
        crashReportManager.install()
        // StrictMode is debug-only: surfaces disk I/O on Main, leaked SQLite
        // cursors, and unclosed Closeables via logcat, and kills the process
        // on main-thread NETWORK. Suppressed in release via the
        // BuildConfig.DEBUG gate - release APKs must not stall on a stray
        // `runOnUiThread { db.query() }`.
        if (BuildConfig.DEBUG) installStrictMode()
        // DiagnosticsInitializer (registered via androidx.startup in the
        // manifest) has already run StartupMetricsCollector,
        // DiagnosticsCollector, and ProfilingCaptureController before
        // Application.onCreate, so the cold-start critical path stays short.
        downloadNotificationCenter.ensureChannel()
        // Drop partial .tmp files (+ resume sidecars) orphaned by a previous
        // process death, then reconcile files no downloads row references.
        // Runs async; startup enqueuers (the coordinator below) await it via
        // DownloadController.awaitColdStartPurge so the sweep can't delete a
        // fresh in-progress partial.
        downloadController.purgeStalePartialsOnColdStart()
        // Idempotent - observes the "Auto-download favorites" toggle and
        // enqueues downloads for any favorited tracks not already on disk.
        // Internally waits for the cold-start sweep above to finish first.
        autoDownloadFavoritesCoordinator.start()
        // Fire-and-forget pre-alpha update check. One-shot per process; the
        // controller swallows errors + mutates shared state that
        // MainActivity's dialog host observes. See AppUpdateController.
        appUpdateController.checkSilently()
        // Off-main-thread look for a crash marker / reportable exit records
        // from the previous run; surfaces the opt-in report sheet hosted by
        // MainActivity. Force-closes never trigger it (REASON_USER_REQUESTED
        // is filtered out).
        crashReportManager.checkOnColdStart()
    }

    /**
     * Voluntarily release caches the OS can regenerate cheaply. Per the
     * Android 17 memory-efficiency guidance, focus on the two levels the OS
     * raises when the UI is no longer visible: TRIM_MEMORY_UI_HIDDEN and
     * TRIM_MEMORY_BACKGROUND. We drop Coil's in-memory bitmap cache (the
     * biggest ephemeral allocation, bounded to 25 % of available memory in
     * [newImageLoader]) and the in-memory "update available" snapshot held
     * by [appUpdateController]. The Coil disk cache, the OkHttp HTTP cache,
     * and the download controller's queue are intentionally untouched -
     * they're either persistent (disk cache) or resumable across the next
     * foreground (downloads, which run under a foreground service).
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI_HIDDEN + BACKGROUND are the only levels we act on; the
        // remaining TRIM_MEMORY_* constants are informational only and
        // would just thrash caches.
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            -> {
                SingletonImageLoader.get(this).memoryCache?.clear()
                appUpdateController.releaseOnTrim()
            }

            else -> Unit
        }
    }

    /**
     * Debug-only policy: log every Main-thread disk read/write, network call,
     * and SQLite leak. `penaltyLog` keeps the app running for disk and
     * slow-call violations, so the dev sees the stack trace without losing UI
     * state.
     *
     * Network is the exception - it is fatal here, matching release. Release
     * APKs inherit the OS-default death-on-network policy, so a blocking HTTP
     * call that reaches Main crashes the user's app while a `penaltyLog`-only
     * debug build merely logs a line nobody reads. That asymmetry is exactly
     * how 0.5.20 shipped a NetworkOnMainThreadException on the SoundCloud tab
     * (fixed in a206868): the provider clients ran OkHttp inside suspend
     * functions with no `withContext`, and `viewModelScope`'s
     * Dispatchers.Main.immediate runs a `launch` body inline when it is
     * already on Main. Dying here makes debug builds fail the same way the
     * shipped APK would, on the first tap rather than in a user's crash log.
     *
     * Disk stays non-fatal: Room, DataStore, and SharedPreferences all do
     * legitimate first-touch reads that would otherwise make the app
     * unstartable in debug.
     */
    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .penaltyDeathOnNetwork()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
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
                        // Shared OkHttp carries PublicHostGuard (redirect
                        // interceptor + DNS) so art URLs cannot redirect /
                        // resolve into private or link-local targets.
                        callFactory = { entryPoint.okHttpClient() },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
                // Canonicalize Bandcamp / YouTube art URLs so every surface
                // (search, queue, detail, player) hits one full-quality
                // disk-cache key and never re-downloads the same cover.
                add(ThumbnailCanonicalInterceptor())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, COIL_MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                // Coil's image cache lives inside the unified downloads pool
                // (filesDir/downloads/images) so its size shows up in the
                // single storage indicator and "Remove all downloads" wipes
                // it alongside audio. Coil manages its own LRU within the
                // configured maxSizeBytes; pinned audio downloads are
                // tracked in Room and not affected by Coil.
                // Sized for full-original Bandcamp `_0` covers (~1-10 MB).
                DiskCache.Builder()
                    .directory(StoragePaths.imagesDir(this))
                    .maxSizeBytes(COIL_DISK_CACHE_BYTES)
                    .build()
            }
            .build()
    }

    private companion object {
        /** Fraction of available memory for Coil's in-memory bitmap cache. */
        private const val COIL_MEMORY_CACHE_PERCENT = 0.25

        /** Coil disk cache budget for full-original covers (~1-10 MB each). */
        private const val COIL_DISK_CACHE_BYTES = 512L * 1024L * 1024L
    }
}
