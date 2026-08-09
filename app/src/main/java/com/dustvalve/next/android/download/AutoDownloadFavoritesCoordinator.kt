package com.dustvalve.next.android.download

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import com.dustvalve.next.android.domain.repository.TrackCacheRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton background worker that auto-downloads every favorited track
 * whenever BOTH "Auto-download future content" AND its "Auto-download
 * favorites" sub-toggle are on.
 *
 * Lifecycle:
 * - [start] is called from [com.dustvalve.next.android.DustvalveNextApplication.onCreate].
 *   It launches a long-lived coroutine on the application scope.
 * - The coroutine `combine`s the parent+child toggle flows with the
 *   favorite-tracks flow. Whenever both toggles are on, every favorited
 *   track that isn't already downloaded gets enqueued via
 *   [DownloadRepository.downloadTrack].
 *
 * Initial scope: tracks-only. Album/artist favorites can be expanded later
 * by also observing `favoriteIds(FavoriteType.ALBUM)` / `favoriteIds(FavoriteType.ARTIST)`
 * and driving `downloadAlbum` / per-album scrape + downloadAlbum respectively
 * (TODO once the UI surfaces a download-progress sink for those flows).
 *
 * Errors are swallowed by design - auto-download must never crash the app
 * or block the UI. Failed tracks just stay non-downloaded; the next favorite
 * change re-evaluates and retries them.
 */
@Singleton
class AutoDownloadFavoritesCoordinator @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val favoriteRepository: FavoriteRepository,
    private val trackCacheRepository: TrackCacheRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadController: DownloadController,
    @Dispatcher(AppDispatchers.IO) ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + ioDispatcher +
            CoroutineExceptionHandler { _, throwable ->
                android.util.Log.e("AutoDownloadFavorites", "Auto-download coordinator failed", throwable)
            },
    )
    private var job: Job? = null

    /** Idempotent. Safe to call from Application.onCreate(). */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            // The cold-start sweep deletes stale .tmp partials (and orphaned
            // finished files). Wait for it before enqueueing anything so it
            // can't race - and delete - a fresh in-progress partial written
            // by work we enqueue below.
            downloadController.awaitColdStartPurge()
            combine(
                settingsDataStore.autoDownloadFutureContent.distinctUntilChanged(),
                settingsDataStore.autoDownloadFavorites.distinctUntilChanged(),
                favoriteRepository.favoriteIds(FavoriteType.TRACK),
                downloadRepository.getDownloadedTrackIds().distinctUntilChanged(),
            ) { futureContent, favoritesToggle, favorites, downloaded ->
                // Favorites is a sub-toggle of "auto-download future content":
                // parent off must stop enqueueing even if the child key is still true.
                Triple(futureContent && favoritesToggle, favorites, downloaded)
            }
                .collectLatest { (enabled, favorites, downloaded) ->
                    if (!enabled) return@collectLatest
                    val missing = favorites.filter { it !in downloaded }
                    if (missing.isEmpty()) return@collectLatest
                    // We're iterating favorites, so force isFavorite = true via
                    // an explicit copy - this preserves the historical hardcoded
                    // true even in the unfavorite race window, where the
                    // repository's live decoration could already say false.
                    val tracks = trackCacheRepository.getTracks(missing).map { it.copy(isFavorite = true) }
                    // Hand off to the controller (foreground service + serial
                    // queue, de-duped by track id). Fire-and-forget - failures
                    // just leave the track non-downloaded and a later emission
                    // re-enqueues it.
                    for (track in tracks) {
                        downloadController.enqueueTrack(track)
                    }
                }
        }
    }

    /**
     * Stops observing favorites. Downloads already handed to
     * [DownloadController] keep running under its foreground service.
     */
    fun stop() {
        job?.cancel()
        job = null
    }
}
