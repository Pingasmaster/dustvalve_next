package com.dustvalve.next.android.download

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getByIds
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * whenever the "Auto-download favorites" toggle is on (sub-toggle of
 * "Auto-download future content").
 *
 * Lifecycle:
 * - [start] is called from [com.dustvalve.next.android.DustvalveNextApplication.onCreate].
 *   It launches a long-lived coroutine on the application scope.
 * - The coroutine `combine`s the toggle flow with the favorite-tracks flow.
 *   Whenever the toggle is on, every favorited track that isn't already
 *   downloaded gets enqueued via [DownloadRepository.downloadTrack].
 *
 * Initial scope: tracks-only. Album/artist favorites can be expanded later
 * by also observing `getAllByType("album")` / `getAllByType("artist")` and
 * driving `downloadAlbum` / per-album scrape + downloadAlbum respectively
 * (TODO once the UI surfaces a download-progress sink for those flows).
 *
 * Errors are swallowed by design — auto-download must never crash the app
 * or block the UI. Failed tracks just stay non-downloaded; the next favorite
 * change re-evaluates and retries them.
 */
@Singleton
class AutoDownloadFavoritesCoordinator @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val favoriteDao: FavoriteDao,
    private val trackDao: TrackDao,
    private val downloadRepository: DownloadRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Idempotent. Safe to call from Application.onCreate(). */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(
                settingsDataStore.autoDownloadFavorites.distinctUntilChanged(),
                favoriteDao.getAllByType("track"),
                downloadRepository.getDownloadedTrackIds().distinctUntilChanged(),
            ) { enabled, favorites, downloaded -> Triple(enabled, favorites, downloaded) }
                .collectLatest { (enabled, favorites, downloaded) ->
                    if (!enabled) return@collectLatest
                    val missing = favorites.map { it.id }.filter { it !in downloaded }
                    if (missing.isEmpty()) return@collectLatest
                    // We're iterating favorites, so isFavorite = true.
                    val tracks = trackDao.getByIds(missing).map { it.toDomain(isFavorite = true) }
                    for (track in tracks) {
                        try {
                            downloadRepository.downloadTrack(track)
                        } catch (_: Throwable) {
                            // Best-effort — next emission re-tries naturally.
                        }
                    }
                }
        }
    }

    /** Stops the worker and cancels in-flight downloads it triggered. */
    fun stop() {
        job?.cancel()
        job = null
    }
}
